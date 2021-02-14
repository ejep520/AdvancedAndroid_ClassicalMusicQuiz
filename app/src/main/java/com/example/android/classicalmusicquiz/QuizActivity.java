/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.classicalmusicquiz;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.session.MediaButtonReceiver;

import com.example.android.classicalmusicquiz.databinding.ActivityQuizBinding;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;

public class QuizActivity extends AppCompatActivity implements View.OnClickListener, EventListener {

    private static final int CORRECT_ANSWER_DELAY_MILLIS = 2000;
    private static final String REMAINING_SONGS_KEY = "remaining_songs";
    private ArrayList<Integer> mRemainingSampleIDs, mQuestionSampleIDs;
    private int mAnswerSampleID, mCurrentScore, mHighScore, mNotificationId;
    private Button[] mButtons;
    private ActivityQuizBinding binding;
    private static MediaSessionCompat mSessionCompat;
    private PlaybackStateCompat.Builder mPlaybackStateBuilder;
    private NotificationManager mNotificationManager;
    private final MediaSessionCompat.Callback MediaSessionCallbacks = new MediaSessionCompat.Callback() {

        @Override
        public void onPlay() {
            if (binding.playerView.getPlayer() != null) {
                binding.playerView.getPlayer().play();
                onPlaybackStateChanged(binding.playerView.getPlayer().getPlaybackState());
            }
        }

        @Override
        public void onPause() {
            if (binding.playerView.getPlayer() != null) {
                binding.playerView.getPlayer().pause();
                onPlaybackStateChanged(binding.playerView.getPlayer().getPlaybackState());
            }
        }

        @Override
        public void onSkipToPrevious() {
            if (binding.playerView.getPlayer() != null) {
                binding.playerView.getPlayer().seekTo(0L);
            }
        }
    };

    public static class MediaReceiver extends BroadcastReceiver {
        public MediaReceiver() {}

        @Override
        public void onReceive(Context context, Intent intent) {
            MediaButtonReceiver.handleIntent(mSessionCompat, intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQuizBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        mSessionCompat = new MediaSessionCompat(this, "MediaSessionCompat");
        mSessionCompat.setMediaButtonReceiver(null);

        mPlaybackStateBuilder = new PlaybackStateCompat.Builder();
        mPlaybackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        mSessionCompat.setPlaybackState(mPlaybackStateBuilder.build());
        mSessionCompat.setCallback(MediaSessionCallbacks);

        boolean isNewGame = !getIntent().hasExtra(REMAINING_SONGS_KEY);

        // If it's a new game, set the current score to 0 and load all samples.
        if (isNewGame) {
            QuizUtils.setCurrentScore(this, 0);
            mRemainingSampleIDs = Sample.getAllSampleIDs(this);
            // Otherwise, get the remaining songs from the Intent.
        } else {
            mRemainingSampleIDs = getIntent().getIntegerArrayListExtra(REMAINING_SONGS_KEY);
        }

        mNotificationId = Sample.getAllSampleIDs(this).size() - mRemainingSampleIDs.size();

        binding.playerView.setDefaultArtwork(ContextCompat
                .getDrawable(this, R.drawable.question_mark));

        // Get current and high scores.
        mCurrentScore = QuizUtils.getCurrentScore(this);
        mHighScore = QuizUtils.getHighScore(this);

        // Generate a question and get the correct answer.
        mQuestionSampleIDs = QuizUtils.generateQuestion(mRemainingSampleIDs);
        mAnswerSampleID = QuizUtils.getCorrectAnswerID(mQuestionSampleIDs);

        // If there is only one answer left, end the game.
        if (mQuestionSampleIDs.size() < 2) {
            QuizUtils.endGame(this);
            finish();
        }

        // Initialize the buttons with the composers names.
        mButtons = initializeButtons(mQuestionSampleIDs);

        mSessionCompat.setActive(true);

        Sample answerSample = Sample.getSampleByID(this, mAnswerSampleID);
        if (answerSample == null) {
            Toast.makeText(this, R.string.sample_not_found_error, Toast.LENGTH_SHORT).show();
            return;
        }
        initializePlayer(Uri.parse(answerSample.getUri()));
    }

    /**
     * Initializes the button to the correct views, and sets the text to the composers names,
     * and set's the OnClick listener to the buttons.
     *
     * @param answerSampleIDs The IDs of the possible answers to the question.
     * @return The Array of initialized buttons.
     **/
    @NonNull
    private Button[] initializeButtons(@NonNull ArrayList<Integer> answerSampleIDs) {
        Button[] buttons = {binding.buttonA, binding.buttonB, binding.buttonC, binding.buttonD};
        if (buttons.length > answerSampleIDs.size()) {
            for (int counter = 3; counter >= 0; counter--) {
                if (counter >= answerSampleIDs.size()) {
                    buttons[counter].setVisibility(View.INVISIBLE);
                }
            }
        }
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i].getVisibility() != View.INVISIBLE) {
                Sample currentSample = Sample.getSampleByID(this, answerSampleIDs.get(i));
                buttons[i].setOnClickListener(this);
                if (currentSample != null) {
                    buttons[i].setText(currentSample.getComposer());
                }
            }
        }
        return buttons;
    }

    /**
     * Initialize ExoPlayer.
     *
     * @param mediaUri The URI of the sample to play.
     */
    private void initializePlayer(Uri mediaUri) {
        if (binding.playerView.getPlayer() != null) {
            return;
        }

        // Create an instance of the ExoPlayer.
        MediaItem mediaItem = MediaItem.fromUri(mediaUri);
        TrackSelector trackSelector = new DefaultTrackSelector(this);
        LoadControl loadControl = new DefaultLoadControl();
        SimpleExoPlayer.Builder builder = new SimpleExoPlayer.Builder(this);
        builder.setTrackSelector(trackSelector);
        builder.setLoadControl(loadControl);
        binding.playerView.setPlayer(builder.build());
        binding.playerView.getPlayer().addListener(this);
        binding.playerView.getPlayer().setMediaItem(mediaItem);
        binding.playerView.getPlayer().setPlayWhenReady(true);
        binding.playerView.getPlayer().prepare();
    }

    /**
     * The OnClick method for all of the answer buttons. The method uses the index of the button
     * in button array to to get the ID of the sample from the array of question IDs. It also
     * toggles the UI to show the correct answer.
     *
     * @param v The button that was clicked.
     */
    @Override
    public void onClick(View v) {

        // Show the correct answer.
        showCorrectAnswer();

        // Get the button that was pressed.
        Button pressedButton = (Button) v;

        // Get the index of the pressed button
        int userAnswerIndex = -1;
        for (int i = 0; i < mButtons.length; i++) {
            if (pressedButton.getId() == mButtons[i].getId()) {
                userAnswerIndex = i;
            }
        }

        // Get the ID of the sample that the user selected.
        int userAnswerSampleID = mQuestionSampleIDs.get(userAnswerIndex);

        // If the user is correct, increase there score and update high score.
        if (QuizUtils.userCorrect(mAnswerSampleID, userAnswerSampleID)) {
            QuizUtils.setCurrentScore(this, ++mCurrentScore);
            if (mCurrentScore > mHighScore) {
                mHighScore = mCurrentScore;
                QuizUtils.setHighScore(this, mHighScore);
            }
        }

        // Remove the answer sample from the list of all samples, so it doesn't get asked again.
        mRemainingSampleIDs.remove(Integer.valueOf(mAnswerSampleID));

        // Wait some time so the user can see the correct answer, then go to the next question.
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            Intent nextQuestionIntent = new Intent(QuizActivity.this, QuizActivity.class);
            nextQuestionIntent.putExtra(REMAINING_SONGS_KEY, mRemainingSampleIDs);
            finish();
            startActivity(nextQuestionIntent);
        }, CORRECT_ANSWER_DELAY_MILLIS);

    }

    /**
     * Disables the buttons and changes the background colors to show the correct answer.
     */
    private void showCorrectAnswer() {
        for (int i = 0; i < mQuestionSampleIDs.size(); i++) {
            int buttonSampleID = mQuestionSampleIDs.get(i);

            mButtons[i].setEnabled(false);
            if (buttonSampleID == mAnswerSampleID) {
                mButtons[i].getBackground().setColorFilter(ContextCompat.getColor
                                (this, android.R.color.holo_green_light),
                        PorterDuff.Mode.MULTIPLY);
            } else {
                mButtons[i].getBackground().setColorFilter(ContextCompat.getColor
                                (this, android.R.color.holo_red_light),
                        PorterDuff.Mode.MULTIPLY);
            }
            mButtons[i].setTextColor(Color.WHITE);
        }
        binding.playerView.setDefaultArtwork(Sample
                .getComposerArtBySampleID(this, mAnswerSampleID));
    }

    public void onPlaybackStateChanged(@Player.State int playbackState) {
        final String LOG_TAG = "onPlaybackStateChanged";
        if (binding.playerView.getPlayer() == null) { return; }
        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                Log.i(LOG_TAG, "The state is now idle.");
                break;
            case ExoPlayer.STATE_BUFFERING:
                Log.i(LOG_TAG, "The state is now buffering.");
                break;
            case ExoPlayer.STATE_READY:
                if (binding.playerView.getPlayer().getPlayWhenReady()) {
                    mPlaybackStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING,
                            binding.playerView.getPlayer().getCurrentPosition(),
                            1f);
                    Log.i(LOG_TAG, "State changed to ready and getPlayWhenReady == true");
                } else {
                    mPlaybackStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED,
                            binding.playerView.getPlayer().getCurrentPosition(),
                            1f);
                    Log.i(LOG_TAG, "State changed to ready and getPlayWhenReady == false");
                }
                mSessionCompat.setPlaybackState(mPlaybackStateBuilder.build());
                showNotification(mPlaybackStateBuilder.build());
                break;
            case ExoPlayer.STATE_ENDED:
                Log.i(LOG_TAG, "State changed to ended.");
        }
    }

    @SuppressWarnings({"unused", "UnusedAssignment"})
    private void showNotification(PlaybackStateCompat state) {

        NotificationCompat.Builder builder;
        int icon;
        String play_pause;
        NotificationCompat.Action playPauseAction, restartAction;
        PendingIntent pendingIntent;
        NotificationChannel channel;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(getString(R.string.app_name), "Classical Quiz", NotificationManager.IMPORTANCE_DEFAULT);
            mNotificationManager.createNotificationChannel(channel);
        }
        builder = new NotificationCompat.Builder(this, getString(R.string.app_name));
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
            icon = R.drawable.exo_controls_pause;
            play_pause = getString(R.string.exo_controls_pause_description);
        } else {
            icon = R.drawable.exo_controls_play;
            play_pause = getString(R.string.exo_controls_play_description);
        }

        playPauseAction = new NotificationCompat.Action(icon, play_pause,
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE));

        restartAction = new NotificationCompat.Action(
                R.drawable.exo_controls_previous,
                getString(R.string.exo_controls_previous_description),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS));

        pendingIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, QuizActivity.class),
                0);

        builder.setContentTitle(getString(R.string.guess))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.exo_ic_default_album_image)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(playPauseAction)
                .addAction(restartAction)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mSessionCompat.getSessionToken())
                        .setShowActionsInCompactView(0, 1));

        mNotificationManager.notify(mNotificationId, builder.build());
    }

    private void releasePlayer(@NonNull PlayerView player) {
        if (player.getPlayer() != null) {
            player.getPlayer().stop();
            player.getPlayer().release();
            player.setPlayer(null);
        }
        mSessionCompat.setActive(false);
        mNotificationManager.cancel(mNotificationId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PlayerView player = findViewById(R.id.playerView);
        if ((player != null) && (binding.playerView.getPlayer() != null)) {
            releasePlayer(binding.playerView);
        }
    }
}
