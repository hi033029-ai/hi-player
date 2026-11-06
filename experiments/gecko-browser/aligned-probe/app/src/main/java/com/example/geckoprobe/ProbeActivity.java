package com.example.geckoprobe;
import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceView;
public class ProbeActivity extends Activity {
    public SurfaceView surface;
    @Override public void onCreate(Bundle state) { super.onCreate(state); surface = new SurfaceView(this); setContentView(surface); }
}
