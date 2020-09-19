package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
//import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.time.Instant;
import java.sql.Timestamp;
import java.time.Instant;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Camera extends AppCompatActivity {
    private static final int PERMISSION_CODE = 1000;
    private static final int IMAGE_CAPTURE_CODE = 1001;
    private static final int PICK_IMAGE = 1;

    String lat, lon;
    Button captureBtn;
    ImageView capturedImg;
    Button submit;
    ProgressBar predbar;
    FirebaseAuth fAuth;
    String userID;
    int f = 0;
    double min = 9999999;
    String cat;
    FirebaseFirestore fStore;
    private StorageReference mStorageRef; // In parent class
    String munid = "";
    Bitmap bitmap;
    Uri image_url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
        final Date date = new Date();
        final String time = String.valueOf(date.getTime());
        capturedImg = findViewById(R.id.image_view);
        captureBtn = findViewById(R.id.captureImage);
        submit = findViewById(R.id.submit);
        predbar = findViewById(R.id.predbar);
        cat = getIntent().getStringExtra("cat");
        lat = getIntent().getStringExtra("lat");
        lon = getIntent().getStringExtra("lon");
        fAuth = FirebaseAuth.getInstance();
        userID= fAuth.getCurrentUser().getUid();
        fStore = FirebaseFirestore.getInstance();

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_DENIED ||
                            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                                    PackageManager.PERMISSION_DENIED) {
                        String[] permission = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
                        requestPermissions(permission, PERMISSION_CODE);
                    }
                    else {
                        openCamera();
                    }
                }
                else{
                    openCamera();
                }
            }
        });

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                predbar.setVisibility(View.VISIBLE);
                mStorageRef = FirebaseStorage.getInstance().getReference(); // On create

                final String img_filename = String.format("%s.jpg", formatter.format(date));
                if(image_url==null){
                    Toast.makeText(Camera.this, "Click an image to continue", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(image_url!=null){
                    final StorageReference imgRef = mStorageRef.child(img_filename);
                    UploadTask uploadTask = imgRef.putFile(image_url);
                    Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                        @Override
                        public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                            if (!task.isSuccessful()) {
                                throw task.getException();
                            }

                            // Continue with the task to get the download URL
                            return imgRef.getDownloadUrl();
                        }
                    }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(@NonNull Task<Uri> task) {
                            if (task.isSuccessful()) {
                                final Uri imguri = task.getResult();
                                if(lat==null && lon==null){
                                    Toast.makeText(Camera.this,"Cannot fetch Location", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                else{
                                    fStore.collection("complaints").get()
                                            .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                @Override
                                                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                    if (task.isSuccessful()) {
                                                        for (QueryDocumentSnapshot document : task.getResult()) {
                                                            if(document.getString("lat")==null && document.getString("lon")==null){
                                                                Toast.makeText(Camera.this, "Issue with existing database entry, check db", Toast.LENGTH_SHORT).show();
                                                                return;
                                                            }
                                                            double lat1 = Double.parseDouble(document.getString("lat"));
                                                            double lon1 = Double.parseDouble(document.getString("lon"));
                                                            double theta = Double.parseDouble(lon) - lon1;
                                                            double dist = Math.sin(Math.toRadians(Double.parseDouble(lat))) * Math.sin(Math.toRadians(lat1)) + Math.cos(Math.toRadians(Double.parseDouble(lat))) * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(theta));
                                                            dist = Math.acos(dist);
                                                            dist = Math.toDegrees(dist);
                                                            dist = dist * 60 * 1.1515;
                                                            dist = dist * 1.609344; //in KM
                                                            if(dist<=0.005 && cat.equals(document.getString("type"))){
                                                                f = 1;
                                                                break;
                                                            }
                                                        }
                                                        if(f==0){
                                                            fStore.collection("org").get()
                                                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                                                        @Override
                                                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                                            if(task.isSuccessful()){
                                                                                for(QueryDocumentSnapshot document : task.getResult()){
                                                                                    double lat2 = Double.parseDouble(document.getString("lat"));
                                                                                    double lon2 = Double.parseDouble(document.getString("lon"));
                                                                                    double theta = Double.parseDouble(lon) - lon2;
                                                                                    double dist = Math.sin(Math.toRadians(Double.parseDouble(lat))) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(Double.parseDouble(lat))) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
                                                                                    dist = Math.acos(dist);
                                                                                    dist = Math.toDegrees(dist);
                                                                                    dist = dist * 60 * 1.1515;
                                                                                    dist = dist * 1.609344; //in KM
                                                                                    if(min>dist){
                                                                                        min=dist;
                                                                                        munid = document.getString("munid");
                                                                                    }
                                                                                }

                                                                                DocumentReference documentReference = fStore.collection("complaints").document(userID+formatter.format(date));
                                                                                Map<String,Object> user = new HashMap<>();
                                                                                user.put("munid",munid);
                                                                                user.put("eflag","false");
                                                                                user.put("status","pending");
                                                                                user.put("time",time);
                                                                                user.put("date",formatter.format(date));
                                                                                Timestamp ts=new Timestamp(date.getTime());
                                                                                user.put("timestamp", ts);
                                                                                user.put("lat",lat);
                                                                                user.put("lon",lon);
                                                                                user.put("image",imguri.toString());
                                                                                user.put("userID",userID);
                                                                                user.put("type",cat);
                                                                                documentReference.set(user).addOnSuccessListener(new OnSuccessListener<Void>() {
                                                                                    @Override
                                                                                    public void onSuccess(Void aVoid) {
                                                                                        Toast.makeText(Camera.this, "Request has been updated to the required authorities", Toast.LENGTH_SHORT).show();
                                                                                        Log.d("Tag", "Request Updated in db");
                                                                                        Intent intent = new Intent(getApplicationContext(), Done.class);
                                                                                        startActivity(intent);
                                                                                        finish();
                                                                                    }
                                                                                });
                                                                            }
                                                                        }
                                                                    });
                                                        }
                                                        else{
                                                            Intent intent = new Intent(getApplicationContext(), EntryExists.class);
                                                            startActivity(intent);
                                                            finish();
                                                        }
                                                    }
                                                }
                                            }).addOnFailureListener(new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            Toast.makeText(Camera.this, "Error occured "+e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });;

                                }
                            } else {
                                Toast.makeText(Camera.this, "Error occured ", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void openCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Picture");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Cam");
        image_url = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        Intent cameraIntent = new  Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, image_url);
        startActivityForResult(cameraIntent, IMAGE_CAPTURE_CODE);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                openCamera();
            }
            else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            capturedImg.setImageURI(image_url);
            Toast.makeText(this, "Camera", Toast.LENGTH_SHORT).show();
        }
        if (requestCode == PICK_IMAGE) {
            try {
                image_url = data.getData();
                capturedImg.setImageURI(image_url);
                Toast.makeText(this, "Picked up from gallery", Toast.LENGTH_SHORT).show();
            } catch (NullPointerException e) {
                Toast.makeText(this, "Nothing Selected", Toast.LENGTH_SHORT).show();
            }
        }
        if (image_url != null) {
            try {
                bitmap = Bitmap.createScaledBitmap(MediaStore.Images.Media.getBitmap(this.getContentResolver(), image_url), 224, 224, true);
            } catch (Exception e) {
                Toast.makeText(this, "No Image", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
