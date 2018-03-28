package com.example.tom.gameenginedemo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Random;

public class SimpleGameEngine extends Activity {

    // gameView will be the view of the game.
    // It will also hold the logic of the game and respond to screen touches
    GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize gameView and set it as the view
        gameView = new GameView(this);
        setContentView(gameView);

    }

    // Here is our implementation of GameView. It is an inner class.
    // We implement runnable so we have a thread and can override the run method.e
    class GameView extends SurfaceView implements Runnable {

        Thread gameThread = null;                   // This is our thread
        SurfaceHolder ourHolder;                    // We need a SurfaceHolder when we use Paint and Canvas
        volatile boolean playing;                   // A boolean that will set/unset when the game is running
        Canvas canvas;                              // A Canvas and a Paint object
        Paint paint;
        Random rand = new Random();
        MediaPlayer chomp, boom, alarm, bgmusic;    // MediaPlayers for sound effects
        long fps;                                   // This variable tracks the game frame rate
        private long timeThisFrame;                 // This is used to help calculate the fps
        long score = 0;                             // Hold the game score
        int ballMax = 20;                           // Set the maximum number of balls
        int screenX, screenY;                       // Screen size in pixels
        Bitmap bitmapDragon, bitmapBG;              // Declare objects of type Bitmap
        boolean faceLeft, ballNear, createBall, isMoving, stunned, bombEvent;
        float density, walkSpeedPerSecond, dragonX, dragonY, touchX, touchY;
        final float openMouth, eatBall;             // Constants that control when the dragon opens and eats
        Rect screenSize;                            // A Rect the size of the screen to draw the background on
        CountDownTimer stunTimer;
        private Handler eventHandler;               // Handler for timed events
        private Runnable ballRun, bombRun;          // Runnable events for the handlers

        // Lists to store balls active in the game
        ArrayList<Ball> ballList = new ArrayList<>();
        ArrayList<Ball> ballRemoveList = new ArrayList<>();
        ArrayList<Ball> bombList = new ArrayList<>();
        ArrayList<Ball> bombRemoveList = new ArrayList<>();

        // When the we initialize (call new()) on gameView, this constructor method runs
        public GameView(Context context) {
            // Use the SurfaceView class to set up our object.
            super(context);

            // Initialize ourHolder and paint objects
            ourHolder = getHolder();
            paint = new Paint();

            // Assign all booleans to start false
            faceLeft = false;
            ballNear = false;
            createBall = false;
            isMoving = false;
            stunned = false;
            bombEvent = false;

            // Get a Display object to access screen details and save to a Point object
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);

            screenX = size.x;
            screenY = size.y;
            screenSize = new Rect(0, 0, screenX, screenY);
            density = getResources().getDisplayMetrics().density; //Log.i("Density", Float.toString(density));
            walkSpeedPerSecond = 150 * density;

            // Constants that control when the dragon opens and eats
            openMouth = 50 * density;
            eatBall = 30 * density;

            // Load .png files to Bitmaps
            bitmapDragon = BitmapFactory.decodeResource(this.getResources(), R.drawable.dragon_right);
            bitmapBG = BitmapFactory.decodeResource(this.getResources(), R.drawable.background);

            // Place him in the center of the screen
            dragonX = screenX / 2;
            dragonY = screenY / 2;

            // Assign MediaPlayers
            chomp = MediaPlayer.create(getApplicationContext(), R.raw.dragon_bite);
            boom = MediaPlayer.create(getApplicationContext(), R.raw.boom);
            alarm = MediaPlayer.create(getApplicationContext(), R.raw.alarm);
            bgmusic = MediaPlayer.create(getApplicationContext(), R.raw.bgmusic);

            //Start background music
            bgmusic.setLooping(true);
            bgmusic.start();

            // Start timers for events
            eventHandler = new Handler();

            ballRun = new Runnable() {
                @Override
                public void run() {
                    createBall = true;
                    eventHandler.postDelayed(this, 500);
                }
            };
            eventHandler.postDelayed(ballRun, 500);

            bombRun = new Runnable() {
                @Override
                public void run() {
                    bombEvent = true;
                    eventHandler.postDelayed(this, rand.nextInt(30001) + 30000);
                }
            };
            eventHandler.postDelayed(bombRun, rand.nextInt(30001) + 30000);
            //eventHandler.postDelayed(bombRun, 1000);

            stunTimer = new CountDownTimer(3000, 300){      // Stuns for 3 seconds, spins every .5 second
                @Override
                public void onTick(long millisUntilFinished) { faceLeft = !faceLeft; }

                @Override
                public void onFinish() {
                    faceLeft = !faceLeft;
                    stunned = false;
                }
            };

            // Set our boolean to true - game on!
            playing = true;
        }

        @Override
        public void run() {
            while (playing) {
                // Capture the current time in milliseconds in startFrameTime
                long startFrameTime = System.currentTimeMillis();

                // Update and draw the frame
                update();
                draw();

                // Calculate the fps this frame and use the result to time animations and more.
                timeThisFrame = System.currentTimeMillis() - startFrameTime;
                if (timeThisFrame > 0) {
                    fps = 1000 / timeThisFrame;
                }
            }
        }

        // Everything that needs to be updated goes in here
        public void update() {

            // If a ball needs to be created, do so
            if (createBall) {
                if (ballList.size() < ballMax) {
                    Ball ball = new Ball(screenX, screenY, getResources(), false);
                    ballList.add(ball);
                }
                createBall = false;
            }

            // If a bombEvent is ready, do it
            if (bombEvent) {
                createBombEvent(rand.nextInt(6) + 5);
                bombEvent = false;
            }

            // Set ballNear to false
            ballNear = false;

            // Update existing balls
            ballRemoveList = updateBalls(ballList);
            bombRemoveList = updateBalls(bombList);

            // If dragon is moving (the player is touching the screen), then move it
            // toward the touch position based on the target speed and the current fps.
            if(isMoving && !stunned){
                double theta = Math.atan2(touchY-dragonY, touchX-dragonX);

                double x = walkSpeedPerSecond * Math.cos(theta);
                float changeX = (float)x;
                double y = walkSpeedPerSecond * Math.sin(theta);
                float changeY = (float)y;

                dragonX = dragonX + (changeX / fps);
                dragonY = dragonY + (changeY / fps);

                faceLeft = changeX < 0;
            }

            // Flip direction dragon is facing depending on movement.
            if (faceLeft){
                if (ballNear){
                    bitmapDragon = BitmapFactory.decodeResource(this.getResources(), R.drawable.dragon_left_open);
                } else {
                    bitmapDragon = BitmapFactory.decodeResource(this.getResources(), R.drawable.dragon_left);
                }
            } else {
                if (ballNear){
                    bitmapDragon = BitmapFactory.decodeResource(this.getResources(), R.drawable.dragon_right_open);
                } else {
                    bitmapDragon = BitmapFactory.decodeResource(this.getResources(), R.drawable.dragon_right);
                }
            }

            // Remove objects in RemoveLists
            ballList.removeAll(ballRemoveList);
            bombList.removeAll(bombRemoveList);

            ballRemoveList.clear();
            bombRemoveList.clear();
        }

        // Draw the newly updated scene
        public void draw() {

            // Make sure our drawing surface is valid or we crash
            if (ourHolder.getSurface().isValid()) {
                // Lock the canvas ready to draw
                canvas = ourHolder.lockCanvas();

                // Draw the background
                canvas.drawBitmap(bitmapBG, null, screenSize, paint);
                //gameView.setBackgroundResource(R.drawable.background);

                // Choose the brush color for drawing
                paint.setColor(Color.argb(255, 0, 0, 0));

                // Make the text a bit bigger
                paint.setTextSize(45 * density);

                // Draw the balls
                drawBalls(ballList);
                drawBalls(bombList);

                // Draw dragon at dragonX, dragonY pixel position
                canvas.drawBitmap(bitmapDragon, dragonX - bitmapDragon.getWidth()/2, dragonY - bitmapDragon.getHeight()/2, paint);

                // Display the current score on the screen
                canvas.drawText("Score:" + score, 20 * density, 40 * density, paint);

                // Draw everything to the screen
                ourHolder.unlockCanvasAndPost(canvas);
            }
        }

        // If SimpleGameEngine Activity is paused/stopped, then shutdown our thread.
        public void pause() {
            playing = false;
            bgmusic.pause();
            try {
                gameThread.join();
            } catch (InterruptedException e) {
                Log.e("Error:", "joining thread");
                e.printStackTrace();
            }
        }

        // If SimpleGameEngine Activity is started, then start our thread.
        public void resume() {
            playing = true;
            bgmusic.start();
            gameThread = new Thread(this);
            gameThread.start();
        }

        // The SurfaceView class implements onTouchListener.
        // We can override this method and detect screen touches.
        @Override
        public boolean onTouchEvent(MotionEvent motionEvent) {
            switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
                // Player has touched the screen
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // Set isMoving so Dragon is moved in the update method
                    isMoving = true;
                    touchX = motionEvent.getX();
                    touchY = motionEvent.getY();
                    break;

                // Player has removed finger from screen
                case MotionEvent.ACTION_UP:
                    // Set isMoving so Dragon does not move
                    isMoving = false;
                    break;
            }
            return true;
        }

        public void createBombEvent(int amount){
            alarm.start();
            Ball bomb;

            for (int i = 0; i < amount; i++){
                bomb = new Ball(screenX, screenY, getResources(), true);
                bombList.add(bomb);
            }
        }

        public ArrayList<Ball> updateBalls(ArrayList<Ball> balls) {
            ArrayList<Ball> removeList = new ArrayList<>();
            for (Ball ball : balls){
                if (ball.getX() < -45 || ball.getX() > screenX + 45){
                    removeList.add(ball);
                } else {
                    float newX = ball.getX() + (ball.getChangeX() / fps);
                    ball.setX(newX);
                    float newY = ball.getY() + (ball.getChangeY() / fps);
                    ball.setY(newY);
                    if (ball.getX() - dragonX < openMouth && ball.getX() - dragonX > -openMouth &&
                            ball.getY() - dragonY < openMouth && ball.getY() - dragonY > -openMouth && !stunned){
                        ballNear = true;
                    }
                    if (ball.getX() - dragonX < eatBall && ball.getX() - dragonX > -eatBall &&
                            ball.getY() - dragonY < eatBall && ball.getY() - dragonY > -eatBall && !stunned){
                        if (chomp.isPlaying())  { chomp.seekTo(0);  }
                        else                    { chomp.start();    }
                        removeList.add(ball);
                        if (ball.getScore() == 0) {
                            // Ball is a bomb if score is 0
                            boom.start();
                            stunned = true;
                            stunTimer.start();
                        } else {
                            score = score + ball.getScore();
                        }
                    }
                }
            }
            return removeList;
        }

        public void drawBalls(ArrayList<Ball> drawables) {
            for (Ball ball : drawables) {
                canvas.drawBitmap(ball.bitmapBall, ball.getX() - ball.bitmapBall.getWidth()/2, ball.getY() - ball.bitmapBall.getHeight()/2, paint);
            }
        }

    } // This is the end of our GameView inner class

    // This method executes when the player starts the game
    @Override
    protected void onResume() {
        super.onResume();
        // Tell the gameView resume method to execute
        gameView.resume();
    }

    // This method executes when the player quits the game
    @Override
    protected void onPause() {
        super.onPause();
        // Tell the gameView pause method to execute
        gameView.pause();
    }
}