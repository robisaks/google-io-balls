package com.rei.googleio;

import static android.hardware.SensorManager.DATA_X;
import static android.hardware.SensorManager.DATA_Y;
import static android.hardware.SensorManager.SENSOR_ACCELEROMETER;
import static android.hardware.SensorManager.SENSOR_DELAY_GAME;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;


import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;
import android.widget.TextView;

/**
 * This activity shows a ball that bounces around. The phone's 
 * accelerometer acts as gravity on the ball. When the ball hits
 * the edge, it bounces back
 */
public class Main extends Activity implements Callback, SensorListener {
	private static final int BALL_RADIUS = 10;
	private SurfaceView surface; 
	private SurfaceHolder holder;
	private final ArrayList<BouncingBallModel> model = new ArrayList<BouncingBallModel>(BALL_RADIUS);
	private List<Float> rebounds = new ArrayList<Float>();
	private GameLoop gameLoop;
	private Paint backgroundPaint;
	private Paint ballPaint;
	private SensorManager sensorMgr;
	private long lastSensorUpdate = -1;
	private Random generator = new Random();
	private int spacingX = 20;
	private int spacingY = 20;
	
	private String preDate = "";
	private long firstTime = -1;
	private Boolean blnUpdate = false;
	@Override 
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	
    	setContentView(R.layout.bouncing_ball);
    	rebounds.add(.8f);
    	rebounds.add(.6f);
    	rebounds.add(.4f);
    	rebounds.add(.2f);
    	
    	surface = (SurfaceView) findViewById(R.id.bouncing_ball_surface);
    	holder = surface.getHolder();
    	surface.getHolder().addCallback(this);
    	
    	backgroundPaint = new Paint();
		backgroundPaint.setColor(Color.WHITE);
		

		ballPaint = new Paint();
		ballPaint.setColor(Color.BLUE);
		ballPaint.setAntiAlias(true);
	
		TextView tv = (TextView) this.findViewById(R.id.tv);  
        
        
        ArrayList<Tweet> tweets = loadTweets();
        String marq = "";
        int i = 0;
		for(Tweet t: tweets){
			if(i > 5) break;
			marq = marq+"'"+t.content+"' ~"+t.author + " | ";
			i++;
		}
		if(marq.equals(""))
			marq = "There was an issue connecting to Twitter... | There was an issue connecting to Twitter... | There was an issue connecting to Twitter... | There was an issue connecting to Twitter...";
		
		tv.setText(marq);
		tv.setSelected(true);  // Set focus to the textview
    }
	public class Tweet {  
        String author;  
        String content;  
	}  
	  
	private ArrayList<Tweet> loadTweets(){  
	    ArrayList<Tweet> tweets = new ArrayList<Tweet>();  
	    try {  
            HttpClient hc = new DefaultHttpClient();  
            HttpGet get = new  
            HttpGet("http://api.twitter.com/1/statuses/user_timeline.json?screen_name=googleio");  
			HttpResponse rp = hc.execute(get);  
			if(rp.getStatusLine().getStatusCode() == HttpStatus.SC_OK)  
			{  
		        String result = EntityUtils.toString(rp.getEntity());
		        System.out.println(result);
		        JSONArray sessions = new JSONArray(result);  
		        for (int i = 0; i < sessions.length(); i++) {  
			        JSONObject session = sessions.getJSONObject(i);  
			        Tweet tweet = new Tweet();  
			        tweet.content = session.getString("text");  
			        tweet.author = "@googleio";  
			                        tweets.add(tweet);  
		        }  
	        }  
    	} catch (Exception e) {  
        Log.e("TwitterFeedActivity", "Error loading JSON", e);  
        }  
        return tweets;  
	}  
	
    private void randomBall(int x,int y, int color){
    	float r1 = generator.nextFloat();
		float r2 = generator.nextFloat();
		while (r2 < .9f){
			r2 += .1f;
		}
		while (r1 < .9f){
			r1 += .1f;
		}
		boolean b1 = generator.nextBoolean();
		boolean b2 = generator.nextBoolean();
		if (!b1) {
			r1 *= -1;
		}
		if (!b2) {
			r2 *= -1;
		}
		BouncingBallModel m = new BouncingBallModel(BALL_RADIUS);
		m.setRebound(.8f);
		m.moveBall(x,y);
		m.setAccel(r1, r2);
		m.setColor(color);
		model.add(m);
    }
	@Override
	protected void onPause() {
		super.onPause();
		
		//model.setVibrator(null);
		
		sensorMgr.unregisterListener(this, SENSOR_ACCELEROMETER);
		sensorMgr = null;
		if(!model.isEmpty())
			for(BouncingBallModel m:model)
				m.setAccel(0, 0);
			
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
		boolean accelSupported = sensorMgr.registerListener(this, 
				SENSOR_ACCELEROMETER,
				SENSOR_DELAY_GAME);
		
		if (!accelSupported) {
			// on accelerometer on this device
			sensorMgr.unregisterListener(this, SENSOR_ACCELEROMETER);
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		if(!model.isEmpty())
			for(BouncingBallModel m:model)
				m.setSize(width, height);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		gameLoop = new GameLoop();
		gameLoop.start();
	}
	
	private void draw() {
		// TODO thread safety - the SurfaceView could go away while we are drawing
		
		Canvas c = null;
		try {
			// NOTE: in the LunarLander they don't have any synchronization here,
			// so I guess this is OK. It will return null if the holder is not ready
			c = holder.lockCanvas();
			
			// TODO this needs to synchronize on something
			if (c != null) {
				doDraw(c);
			}
		} finally {
			if (c != null) {
				holder.unlockCanvasAndPost(c);
			}
		}
	}
	
	private void doDraw(Canvas c) {
		int width = c.getWidth();
		int height = c.getHeight();
		c.drawRect(0, 0, width, height, backgroundPaint);
		
		BitmapDrawable background;
		background = new BitmapDrawable(BitmapFactory.decodeResource(getResources(),R.drawable.back));

		//in this case, you want to tile the entire view
		background.setBounds(0, 0, width, height);
		background.setTileModeXY(TileMode.REPEAT,TileMode.REPEAT);
		background.draw(c);
		GregorianCalendar calendar =  new GregorianCalendar(2011, 4, 10, 7, 0);
		calendar.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
		java.util.Date io = calendar.getTime();
		long today = System.currentTimeMillis();
	    long diff = io.getTime() - today;
	    long days = (diff / (1000 * 60 * 60 * 24));
        long hours = (diff - days * (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (diff - days * (1000 * 60 * 60 * 24) - hours * (1000 * 60 * 60 ) ) / (1000 * 60  );
        long seconds = (diff - days * (1000 * 60 * 60 * 24) - hours * (1000 * 60 * 60 ) - minutes * (1000 * 60)) / (1000  );
        
        String daysString = Long.toString(days); 
        if(daysString.length() < 2)
        	daysString = "0"+days;
        
        String hoursString = Long.toString(hours); 
        if(hoursString.length() < 2)
        	hoursString = "0"+hours;
        
        String minString = Long.toString(minutes); 
        if(minString .length() < 2)
        	minString  = "0"+minutes;
        
        String secString = Long.toString(seconds); 
        if(secString .length() < 2)
        	secString  = "0"+seconds;
        
	    String dateString = daysString+":"+hoursString+":"+minString+":"+secString;//
	    
	    
	    int x = 20, y = 100;
	    int xStart = x;
	    int yStart = 100;
        for (int i = 0; i < dateString.length(); i++)
        {
        	char tile = dateString.charAt(i);
        	String curnum = numberString(Character.digit(tile, 10));
        	if(tile == ':'){
        		
        		ballPaint.setColor(getResources().getColor(R.color.dark_grey));
        		c.drawCircle(x+4, 140, BALL_RADIUS, ballPaint);
        		c.drawCircle(x+4, 180, BALL_RADIUS, ballPaint);
        		x += spacingX*1.5;
        		continue;
        	}
        	List<Integer> balls = new ArrayList<Integer>();
        	if(!preDate.equals(""))
	        	if(tile != preDate.charAt(i)){
	        		int[] ball_list = numberChange(Character.digit(tile, 10));
	        		for (int bl = 0; bl < ball_list.length; bl++) 
	        			balls.add(ball_list[bl]-1);
	        	}
        	xStart = x;		
        	
        	for (int i2 = 0; i2 < curnum.length(); i2++)
	        {
        		int color = R.color.second;
        		switch(i){
	            	case 0:
	            	case 1:
	            		color = R.color.day;
	            		break;
	            	case 3:
	            	case 4:
	            		color = R.color.hour;
	            		break;
	            	case 6:
	            	case 7:
	            		color = R.color.minute;
	            		break;
	            	case 9:
	            	case 10:
	            		
	            		color = R.color.second;
	            		break;
            	}
        		ballPaint.setColor(getResources().getColor(color));
                char tile2 = curnum.charAt(i2);
                if(!preDate.equals(""))
    	        	if(tile != preDate.charAt(i))
		                if(balls.contains(i2)){
		                	randomBall(x,y, color);
		                	for(BouncingBallModel m:model)
		            			m.setSize(c.getWidth(), c.getHeight());
		                }
		                
                //System.out.println("i="+i+"|i2="+i2+"|tile2="+tile2+"|x="+x+"|y="+y+"|xStart="+xStart);
                switch (tile2)
                {
                        case '1':
                        	c.drawCircle(x, y, BALL_RADIUS, ballPaint);    
                            x += spacingX;
                            break;
                        case '0':
                        	ballPaint.setColor(getResources().getColor(R.color.grey));
                    		c.drawCircle(x, y, BALL_RADIUS, ballPaint);
                            x += spacingX;
                            break;
                        case '\n':
                        		
                                y += spacingY;
                                x = xStart;
                                break;
                }
               
	
	        }
        	y = yStart ;
        	x += (spacingX * 4)  + (5);
        	
        }
        preDate = dateString;
		//ballPaint.setColor(Color.BLUE);
		
		
		List<BouncingBallModel> rem = new ArrayList<BouncingBallModel>();
		if(!model.isEmpty()){
			for(BouncingBallModel m:model){
				float ballX, ballY;
				long mbirth;
				synchronized (m.LOCK) {
					ballX = m.ballPixelX;
					ballY = m.ballPixelY;
					mbirth = m.birth;
				
				}
				long curTime = System.currentTimeMillis();
				long elapsedMs = curTime - mbirth;
				long life = elapsedMs / 1000;
				if(life > 5){
					m.setAlpha(m.getAlpha()-15);
				}
				if(m.getAlpha() < 20){
					rem.add(m);
					continue;
				}
				ballPaint.setColor(getResources().getColor(m.getColor()));
				ballPaint.setAlpha(m.getAlpha());
				
				c.drawCircle(ballX, ballY, BALL_RADIUS, ballPaint);
			}
			for(BouncingBallModel remove:rem)
				model.remove(model.indexOf(remove));
		}
		
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		try {
			if(!model.isEmpty())
				for(BouncingBallModel m:model)
					m.setSize(0,0);
			gameLoop.safeStop();
		} finally {
			gameLoop = null;
		}
	}
    
	private class GameLoop extends Thread {
		private volatile boolean running = true;
		
		public void run() {
			while (running) {
				try {
					// TODO don't like this hardcoding
					TimeUnit.MILLISECONDS.sleep(5);
					
					draw();
					for(BouncingBallModel m:model)
						m.updatePhysics();
					
				} catch (InterruptedException ie) {
					running = false;
				}
			}
		}
		
		public void safeStop() {
			running = false;
			interrupt();
		}
	}

	public void onAccuracyChanged(int sensor, int accuracy) {		
	}
	
	public static String numberString(int n){
		String nm;
		switch(n){
			case 1:nm = "0001\n0001\n0001\n0001\n0001\n0001\n0001\n";break;
			case 2:nm = "1111\n0001\n0001\n1111\n1000\n1000\n1111\n";break;
			case 3:nm = "1111\n0001\n0001\n1111\n0001\n0001\n1111\n";break;
			case 4:nm = "1001\n1001\n1001\n1111\n0001\n0001\n0001\n";break;
			case 5:nm = "1111\n1000\n1000\n1111\n0001\n0001\n1111\n";break;
			case 6:nm = "1111\n1000\n1000\n1111\n1001\n1001\n1111\n";break;
			case 7:nm = "1111\n0001\n0001\n0001\n0001\n0001\n0001\n";break;
			case 8:nm = "1111\n1001\n1001\n1111\n1001\n1001\n1111\n";break;
			case 9:nm = "1111\n1001\n1001\n1111\n0001\n0001\n0001\n";break;
		   default:nm = "1111\n1001\n1001\n1001\n1001\n1001\n1111\n";break;
		}
		return nm;
	}
	public static int[] numberChange(int n){
		int[] ball_list = {1};
		switch(n){
	    	case 1:ball_list = new int[] { 1,2,3,13,14,15,16,25,26,27};break;
	    	case 2:ball_list = new int[] { 20,24};break;
	    	case 3:ball_list = new int[] { 5,9};break;
	    	case 4:ball_list = new int[] { 2,3,25,26,27};break;
	    	case 5:ball_list = new int[] { 17,21};break;
	    	case 6:ball_list = new int[] { 2,3,4,8,12};break;
	    	case 7:ball_list = new int[] { 5,9,13,14,15,17,21,25,26,27};break;
	    	case 8:ball_list = new int[] {};break;
	    	case 9:ball_list = new int[] { 17,21,25,26,27};break;
	    	case 0:ball_list = new int[] {};break;	    	
		}
		
		return ball_list;
		
	}
	public void onSensorChanged(int sensor, float[] values) {
		if (sensor == SENSOR_ACCELEROMETER) {
			long curTime = System.currentTimeMillis();
			// only allow one update every 50ms, otherwise updates
			// come way too fast
			if (!blnUpdate) {
				if (firstTime == -1) {
					firstTime = curTime;
				}
				if ((curTime - firstTime) > 500) {
					blnUpdate = true;
				}
			} else {
				if (lastSensorUpdate == -1
						|| (curTime - lastSensorUpdate) > 500) {
					lastSensorUpdate = curTime;
					if (!model.isEmpty())
						for (BouncingBallModel m : model)
							m.setAccel(values[DATA_X], values[DATA_Y]);
				}
			}
	
		}
	}
}
