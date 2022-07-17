package com.filestack.android.internal;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.filestack.android.R;
import com.filestack.android.Selection;

import java.io.File;

public class ImagePreviewActivity extends AppCompatActivity {

    ImageView previewImageView;
    Button acceptButton;
    Button cancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.filestack__activity_image_preview);
        previewImageView = (ImageView)findViewById(R.id.previewImage);

        Bitmap myBitmap = BitmapFactory.decodeFile(Util.getSelectionSaver().getItems().get(0).getPath());
        previewImageView.setImageBitmap(myBitmap);

        acceptButton = (Button) findViewById(R.id.acceptButton);
        acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                acceptImage();
            }
        });
        cancelButton = (Button)findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                denyImage();
            }
        });
    }

    private void acceptImage() {
        setResult(RESULT_OK);
        finish();
    }

    private void denyImage() {
        Util.getSelectionSaver().clear();
        finish();
    }
}