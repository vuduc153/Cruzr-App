package com.example.cruzr;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.cruzr.api.RobotAPI;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;
import com.ubtechinc.cruzr.serverlibutil.interfaces.InitListener;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView textView = findViewById(R.id.dummyText);
        RobotAPI api = RobotAPI.getInstance();

        findViewById(R.id.action1).setOnClickListener(v -> {
            textView.setText(api.getRosVersion());
            RosRobotApi.get().ledSetOnOff(true);
            Integer ret = RosRobotApi.get().moveToward(0.1f, 0, 0);
            Log.i("Move", ret.toString());
        });

        findViewById(R.id.action2).setOnClickListener(v -> {
            textView.setText(api.getRosIP());
            RosRobotApi.get().ledSetOnOff(false);
            RosRobotApi.get().stopMove();
        });
    }
}