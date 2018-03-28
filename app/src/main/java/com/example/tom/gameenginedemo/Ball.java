package com.example.tom.gameenginedemo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.Random;

public class Ball {
    // The location of the ball
    private float ballX;
    private float ballY;

    // The speed the ball will travel to the co-ordinates
    private float changeX, changeY;

    // The score value of the ball
    private int score;

    // Bitmap for the ball
    Bitmap bitmapBall;

    // Constructor method for Balls
    public Ball(int screenX, int screenY, Resources res, boolean bomb){
        // Random generator for start/end position
        Random rand = new Random();
        int type = rand.nextInt(100);

        // The co-ordinates the ball will travel to
        float destX, destY;

        if (bomb){
            // It's a bomb!
            score = 0;
            bitmapBall = BitmapFactory.decodeResource(res, R.drawable.bomb);

        } else if (type < 5){
            // Make the ball blue
            score = 10;
            bitmapBall = BitmapFactory.decodeResource(res, R.drawable.ballblue);

        } else if (type < 40){
            // Make the ball green
            score = 3;
            bitmapBall = BitmapFactory.decodeResource(res, R.drawable.ballgreen);

        } else {
            // Make the ball red
            score = 1;
            bitmapBall = BitmapFactory.decodeResource(res, R.drawable.ballred);
        }

        // Set starting position of ball
        boolean isVertical = rand.nextBoolean();
        boolean startLeft = rand.nextBoolean();

        if (isVertical) {
            ballX = rand.nextInt(screenX);
            destX = rand.nextInt(screenX);

            ballY = startLeft ? -45 : screenY + 45;
            destY = startLeft ? screenY + 45 : -45;
        } else {
            ballX = startLeft ? -45 : screenX + 45;
            destX = startLeft ? screenX + 45 : -45;

            ballY = rand.nextInt(screenY);
            destY = rand.nextInt(screenY);
        }

        // Set motion of ball toward destination
        double theta = Math.atan2(destY-ballY, destX-ballX);

        double x = 100 * res.getDisplayMetrics().density * Math.cos(theta);
        changeX = (float)x;
        double y = 100 * res.getDisplayMetrics().density * Math.sin(theta);
        changeY = (float)y;
    }

    public float getX(){
        return ballX;
    }

    public float getY(){
        return ballY;
    }

    public void setX(float x){
        ballX = x;
    }

    public void setY(float y){
        ballY = y;
    }

    public float getChangeX(){
        return changeX;
    }

    public float getChangeY(){
        return changeY;
    }

    public int getScore(){
        return score;
    }
}
