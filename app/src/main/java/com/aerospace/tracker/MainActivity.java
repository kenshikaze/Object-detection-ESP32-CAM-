package com.aerospace.tracker;

import android.app.Activity;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.*;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import android.graphics.drawable.*;

public class MainActivity extends Activity {
    ImageView image; TextView status, telemetry; EditText streamUrl;
    ObjectDetector detector; ExecutorService streamExecutor=Executors.newSingleThreadExecutor(); ExecutorService commandExecutor=Executors.newFixedThreadPool(2);
    Handler main=new Handler(Looper.getMainLooper());
    volatile boolean running=false;
    Bitmap latestBitmap;
    int lastW=1,lastH=1;
    long lastCommand=0; String lastDir="CENTER";

    // TEMPORARY: these are deliberately placeholders until tomorrow's button URLs
    // are discovered. Change only the endpoint strings once known.
    final String BASE="http://192.168.4.1";
    final String LEFT="/pan?angle=150";
    final String RIGHT="/pan?angle=30";
    final String CENTER="/home";
    final String UP="/tilt?angle=135";
    final String DOWN="/tilt?angle=45";

    @Override public void onCreate(Bundle b){
        super.onCreate(b); setContentView(R.layout.activity_main);
        image=findViewById(R.id.image); status=findViewById(R.id.status);
        telemetry=findViewById(R.id.telemetry); streamUrl=findViewById(R.id.streamUrl);
        ObjectDetectorOptions options=new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                        .enableMultipleObjects()
                                .enableClassification()
                                        .build();

                                        detector=ObjectDetection.getClient(options);

        findViewById(R.id.startBtn).setOnClickListener(v->start());
        findViewById(R.id.stopBtn).setOnClickListener(v->stop());
        findViewById(R.id.leftBtn).setOnClickListener(v->send(LEFT));
        findViewById(R.id.rightBtn).setOnClickListener(v->send(RIGHT));
        findViewById(R.id.centerBtn).setOnClickListener(v->send(CENTER));
        findViewById(R.id.upBtn).setOnClickListener(v->send(UP));
        findViewById(R.id.downBtn).setOnClickListener(v->send(DOWN));
    }

void start(){
        if (running) return;
        running = true;
        status.setText("Connecting to ESP32 camera...");

        String url = streamUrl.getText().toString().trim();

        streamExecutor.execute(() -> readCapture(url));
    }

    void stop(){
        running = false;
        status.setText("Stopped");
    }

    void readCapture(String url){
        while(running){
            HttpURLConnection c = null;

            try{
                URL u = new URL(url);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(5000);
                c.setUseCaches(false);
                c.connect();

                try(InputStream in = new BufferedInputStream(c.getInputStream(), 64 * 1024)){
                    byte[] data = readAll(in);
                    Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);

                    if(bm != null){
                        analyze(bm);
                    }
                }

                // Keep the capture rate modest because the ESP32-CAM is relatively slow.
                Thread.sleep(350);

            } catch(Exception e){
                main.post(() ->
                    status.setText("Capture error: " + e.getClass().getSimpleName())
                );

                try{
                    Thread.sleep(700);
                }catch(Exception ignored){}
            } finally {
                if(c != null) c.disconnect();
            }
        }
    }

    byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
        byte[] buffer = new byte[8192];
        int n;

        while((n = in.read(buffer)) != -1){
            out.write(buffer, 0, n);
        }

        return out.toByteArray();
    }

    void analyze(Bitmap bm){
        latestBitmap=bm; lastW=bm.getWidth(); lastH=bm.getHeight();
main.post(() -> image.setImageBitmap(bm));
        InputImage input=InputImage.fromBitmap(bm,0);
        detector.process(input).addOnSuccessListener(commandExecutor, objects->{
            if(objects.isEmpty()){
                main.post(()->telemetry.setText("No object detected"));
                return;
            }
            DetectedObject target=objects.get(0); // most prominent object
            Rect r=target.getBoundingBox();
            float cx=(r.left+r.right)/2f, cy=(r.top+r.bottom)/2f;
            float nx=cx/lastW, ny=cy/lastH;
            String pan=nx<0.40?"LEFT":(nx>0.60?"RIGHT":"CENTER");
            String tilt=ny<0.40?"UP":(ny>0.60?"DOWN":"CENTER");
            Integer id=target.getTrackingId();
            String msg="Object"+(id==null?"":" #"+id)+"  x="+Math.round(nx*100)+"% y="+Math.round(ny*100)+"%  PAN="+pan+" TILT="+tilt;
            main.post(()->telemetry.setText(msg));
            // conservative command rate: don't hammer the ESP32
            long now=System.currentTimeMillis();
            if(now-lastCommand>350){
                if(!pan.equals("CENTER")) send(pan.equals("LEFT")?LEFT:RIGHT);
                if(!tilt.equals("CENTER")) send(tilt.equals("UP")?UP:DOWN);
                lastCommand=now;
            }
        }).addOnFailureListener(commandExecutor,e->main.post(()->telemetry.setText("Detector error: "+e.getMessage())));
    }

    void send(String path){
        commandExecutor.execute(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();
                c.setConnectTimeout(1200); c.setReadTimeout(1200);
                c.getResponseCode(); c.disconnect();
                main.post(()->status.setText("Command sent: "+path));
            }catch(Exception e){
                main.post(()->status.setText("Command failed: "+path));
            }
        });
    }

    @Override protected void onDestroy(){
        running=false; detector.close(); streamExecutor.shutdownNow(); commandExecutor.shutdownNow(); super.onDestroy();
    }
}
