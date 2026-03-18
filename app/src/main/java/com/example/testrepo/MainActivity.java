package com.example.testrepo;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_new_receipt).setOnClickListener(
                view -> startActivity(new Intent(this, NewReceiptActivity.class))
        );
        findViewById(R.id.button_contents).setOnClickListener(
                view -> startActivity(new Intent(this, ContactsActivity.class))
        );
    }
}
