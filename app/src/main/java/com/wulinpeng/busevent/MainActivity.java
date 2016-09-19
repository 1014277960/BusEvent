package com.wulinpeng.busevent;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("Debug", "Main thread" + Thread.currentThread());
        BusEvent.getInstance().register(this);
        BusEvent.getInstance().post("1");
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void test(String a) {
        Log.d("Debug", "invoke thread" + Thread.currentThread());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BusEvent.getInstance().unRegister(this);
    }
}
