package com.example.androidcamera;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.example.androidcamera.VideoActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Crear un Intent para iniciar la VideoActivity
        Intent intent = new Intent(MainActivity.this, VideoActivity.class);

        // Iniciar la VideoActivity
        startActivity(intent);
    }
}
