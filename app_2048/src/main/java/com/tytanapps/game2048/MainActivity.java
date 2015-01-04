package com.tytanapps.game2048;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesActivityResultCodes;
import com.google.android.gms.games.quest.Quest;
import com.google.android.gms.games.quest.QuestUpdateListener;
import com.google.android.gms.games.quest.Quests;
import com.google.android.gms.games.request.GameRequest;
import com.google.android.gms.games.request.Requests;
import com.google.example.games.basegameutils.BaseGameActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;


public class MainActivity extends BaseGameActivity implements QuestUpdateListener {

    private final static String LOG_TAG = MainActivity.class.getSimpleName();

    public static final String GITHUB_URL = "https://github.com/TylerCarberry/2048-for-Android";
    public static final String APP_URL = "https://play.google.com/store/apps/details?id=com.tytanapps.game2048";

    private final static int SEND_REQUEST_CODE = 1001;
    private final static int SEND_GIFT_CODE = 1002;
    private final static int SHOW_INBOX = 1003;
    private final static int QUEST_CODE = 1004;

    private final static int FLYING_TILE_SPEED = 3000;

    private static boolean activityIsVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        activityIsVisible = true;

        // Give the user a bonus if a day has past since they last played
        addWelcomeBackBonus();

        // Used to debug only
        if(false) {
            try {
                incrementPowerupInventory(5);
                incrementUndoInventory(5);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        updateInventoryTextView();

        super.onStart();
    }

    @Override
    public void onResume() {
        animateFlyingTiles(500, 200);
        checkIfQuestActive();
        checkPendingPlayGifts();
        super.onResume();
    }

    @Override
    protected void onStop() {
        activityIsVisible = false;
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        //if (hasFocus)
        //    hideSystemUI();
    }

    /**
     * Adds a power up or undo to the user's inventory if a day has passed since they last played
     * If the user changed the date to cheat they must wait 3 days
     */
    public void addWelcomeBackBonus() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // The number of days since the epoch
        long lastDatePlayed = prefs.getLong("lastDatePlayed", -1);
        long currentDate = TimeUnit.MILLISECONDS.toDays(Calendar.getInstance().getTimeInMillis());

        if (currentDate > lastDatePlayed) {
            displayWelcomeBackBonus();
        }
        else
            // The time was changed
            if (currentDate < lastDatePlayed) {
                Toast.makeText(this, "You changed the date", Toast.LENGTH_LONG).show();

                // The user must wait another 3 days
                currentDate = lastDatePlayed + 3;
            }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("lastDatePlayed", currentDate);
        editor.apply();
    }

    private void displayWelcomeBackBonus() {
        // Create a new dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.welcome_back));

        // Add either 1 or 2 bonus items
        int bonusAmount = (int) (Math.random() * 2 + 1);

        try {
            if (Math.random() < 0.5) {
                //Toast.makeText(this, "1", Toast.LENGTH_SHORT).show();
                incrementPowerupInventory(bonusAmount);
                builder.setMessage("You Gained " + bonusAmount + " Powerup!\n" +
                        "Come back tomorrow for more.");
            }
            else {
                //Toast.makeText(this, "2", Toast.LENGTH_SHORT).show();
                incrementUndoInventory(bonusAmount);
                builder.setMessage("You Gained " + bonusAmount + " Undo!\n" +
                        "Come back tomorrow for more.");
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_claim_bonus), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to access save file to add random bonus", Toast.LENGTH_LONG).show();
        }

        // Show the message to the player
        builder.create().show();
    }

    public void setQuestButtonEnabled(boolean enabled) {
        ImageButton questsButton = (ImageButton) findViewById(R.id.quests_button);
        questsButton.setImageResource((enabled) ? R.drawable.games_quests_green : R.drawable.games_quests);
    }

    public void updateInventoryTextView() {
        File gameDataFile = new File(getFilesDir(), getString(R.string.file_game_stats));
        GameData gameData = new GameData();
        try {
            gameData = (GameData) Save.load(gameDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        TextView undoTextView = (TextView) findViewById(R.id.undo_inventory);
        undoTextView.setText(""+gameData.getUndoInventory());

        TextView powerupTextView = (TextView) findViewById(R.id.powerup_inventory);
        powerupTextView.setText(""+gameData.getPowerupInventory());
    }

    public void animateFlyingTiles(final int amount, final int delay) {
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            int times = 0;
            @Override
            public void run() {
                if(times > amount || !activityIsVisible)
                    timer.cancel();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        animateFlyingTile();
                    }
                });
                times++;

            }
        }, delay, delay);
    }

    public void animateFlyingTile() {
        final RelativeLayout mainFragment = (RelativeLayout) findViewById(R.id.main_fragment_background);
        if(mainFragment == null)
            return;

        final ImageView tile = new ImageView(this);

        double rand = Math.random();
        if(rand < 0.1)
            tile.setBackgroundResource(R.drawable.tile_2);
        else if(rand < 0.2)
            tile.setBackgroundResource(R.drawable.tile_4);
        else if(rand < 0.3)
            tile.setBackgroundResource(R.drawable.tile_8);
        else if(rand < 0.4)
            tile.setBackgroundResource(R.drawable.tile_16);
        else if(rand < 0.5)
            tile.setBackgroundResource(R.drawable.tile_32);
        else if(rand < 0.6)
            tile.setBackgroundResource(R.drawable.tile_64);
        else if(rand < 0.7)
            tile.setBackgroundResource(R.drawable.tile_256);
        else if(rand < 0.8)
            tile.setBackgroundResource(R.drawable.tile_512);
        else if(rand < 0.9)
            tile.setBackgroundResource(R.drawable.tile_1024);
        else
            tile.setBackgroundResource(R.drawable.tile_2048);

        Display display = getWindowManager().getDefaultDisplay();

        int startingX, startingY, endingX, endingY;

        if(Math.random() > 0.5) {
            startingX = (int) (Math.random() * display.getWidth()) - 200;
            startingY = -200;
        }
        else {
            startingX = -200;
            startingY = (int) (Math.random() * display.getHeight()) - 200;
        }

        if(Math.random() > 0.5) {
            endingX = (int) (Math.random() * display.getWidth()) + 200;
            endingY = display.getHeight() + 200;
        }
        else {
            endingX = display.getWidth() + 200;
            endingY = (int) (Math.random() * display.getHeight() + 200);
        }

        if(Math.random() > 0.5) {
            int temp = startingX;
            startingX = endingX;
            endingX = temp;

            temp = startingY;
            startingY = endingY;
            endingY = temp;
        }

        ObjectAnimator animatorX = ObjectAnimator.ofFloat(tile, View.TRANSLATION_X, endingX - startingX);
        ObjectAnimator animatorY = ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, endingY - startingY);

        float[] rotateAmount = {(float) (2 * (Math.random() - 0.5) * 360), (float) (2 * (Math.random() - 0.5) * 360)};
        ObjectAnimator rotateAnimation = ObjectAnimator.ofFloat(tile, View.ROTATION, rotateAmount);

        animatorX.setDuration(FLYING_TILE_SPEED);
        animatorY.setDuration(FLYING_TILE_SPEED);
        rotateAnimation.setDuration(FLYING_TILE_SPEED);


        animatorX.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mainFragment.removeView(tile);
            }

            @Override
            public void onAnimationStart(Animator animation) {}
            @Override
            public void onAnimationCancel(Animator animation) {}
            @Override
            public void onAnimationRepeat(Animator animation) {}
        });

        RelativeLayout.LayoutParams layoutParams=new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(startingX, startingY, 0, 0);
        tile.setLayoutParams(layoutParams);

        mainFragment.addView(tile);

        animatorX.start();
        animatorY.start();
        rotateAnimation.start();
    }

    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.single_player_imagebutton:
                startActivity(new Intent(this, SelectModeActivity.class));
                break;
            case R.id.multiplayer_imagebutton:
                Intent multiplayerIntent = new Intent(getBaseContext(), MultiplayerActivity.class);
                multiplayerIntent.putExtra("startMultiplayer", true);
                startActivity(multiplayerIntent);
                break;
            case R.id.help_button:
                showHelpDialog();
                break;
            case R.id.settings_button:
                Intent showSettings = new Intent(this, SettingsActivity.class);
                startActivity(showSettings);
                break;
            case R.id.logo_imageview:
                animateFlyingTiles(150, 100);
                break;
            case R.id.custom_game_button:
                startActivity(new Intent(this, CustomGameActivity.class));
        }
    }

    /**
     * Called when either the achievements, leaderboards, or quests buttons are pressed
     * @param view The button that was pressed
     */
    public void playGames(View view) {
        if(getApiClient().isConnected()) {
            switch (view.getId()) {
                case R.id.achievements_button:
                    startActivityForResult(Games.Achievements.getAchievementsIntent(getApiClient()), 1);
                    break;
                case R.id.quests_button:
                    showQuests();
                    break;
                case R.id.leaderboards_button:
                    startActivityForResult(Games.Leaderboards.getAllLeaderboardsIntent(getApiClient()), 0);
                    break;
                case R.id.gifts_button:
                    sendGift();
                    break;
                case R.id.inbox_button:
                    startActivityForResult(Games.Requests.getInboxIntent(getApiClient()), SHOW_INBOX);
                    break;
            }
        }
        else {
            Toast.makeText(this, getString(R.string.not_signed_in_error), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Display all quests
     */
    protected void showQuests() {
        // In the developer tutorial they use Quests.SELECT_ALL_QUESTS
        // but that is not valid for me. That may require an update
        // but for now selecting all possibilities works the same way
        int[] questParams = new int[8];
        questParams[0] = Games.Quests.SELECT_ACCEPTED;
        questParams[1] = Games.Quests.SELECT_OPEN;
        questParams[2] = Games.Quests.SELECT_ENDING_SOON;
        questParams[3] = Games.Quests.SELECT_UPCOMING;
        questParams[4] = Games.Quests.SELECT_COMPLETED;
        questParams[5] = Games.Quests.SELECT_COMPLETED_UNCLAIMED;
        questParams[6] = Games.Quests.SELECT_FAILED;
        questParams[7] = Games.Quests.SELECT_EXPIRED;

        Intent questsIntent = Games.Quests.getQuestsIntent(getApiClient(), questParams);

        // 0 is an arbitrary integer
        startActivityForResult(questsIntent, QUEST_CODE);
    }

    protected void showHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.how_to_play));

        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        int padding = (int) getResources().getDimension(R.dimen.activity_horizontal_margin);

        // The text instructions
        TextView textView = new TextView(this);
        textView.setText(getString(R.string.instructions_to_play));
        textView.setTextSize(22);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setPadding(0, padding, 0, padding);

        // VideoView with game animation how to play
        VideoView videoview = new VideoView(this);
        Uri uri = Uri.parse("android.resource://"+getPackageName()+"/"+R.raw.how_to_play_2048);

        videoview.setVideoURI(uri);
        videoview.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
            }
        });
        videoview.setZOrderOnTop(true);
        videoview.start();


        TextView openSource = new TextView(this);
        openSource.setText(getString(R.string.open_source) + " " + getString(R.string.creator_name));
        openSource.setTextSize(17);
        openSource.setPadding(padding, padding, padding, padding);

        openSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(GITHUB_URL));
                startActivity(i);
            }
        });

        linearLayout.addView(textView);
        linearLayout.addView(videoview);
        linearLayout.addView(openSource);

        builder.setView(linearLayout);
        builder.create().show();

        sendAnalyticsEvent("MainActivity", "Button Press", "Help");
    }

    private void sendAnalyticsEvent(String categoryId, String actionId, String labelId) {
        // Get tracker.
        Tracker t = ((MainApplication)getApplication()).getTracker(MainApplication.TrackerName.APP_TRACKER);
        // Build and send an Event.
        t.send(new HitBuilders.EventBuilder()
                .setCategory(categoryId)
                .setAction(actionId)
                .setLabel(labelId).build());
    }

    protected void showThemesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Themes");

        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        //Button defaultTheme = new Button(this);
        //defaultTheme.setText("Default");

        Button customTheme = new Button(this);
        customTheme.setText("Custom");
        customTheme.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), CustomIconActivity.class));
            }
        });

        //linearLayout.addView(defaultTheme);
        linearLayout.addView(customTheme);

        builder.setView(linearLayout);
        builder.create().show();
    }

    protected void sendGift() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.prompt_choose_gift)).setItems(R.array.gifts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Intent intent;
                // The 'which' argument contains the index position
                // of the selected item
                switch (which) {
                    // Powerup
                    case 0:
                        intent = Games.Requests.getSendIntent(getApiClient(), GameRequest.TYPE_GIFT,
                                "p".getBytes(), Requests.REQUEST_DEFAULT_LIFETIME_DAYS, BitmapFactory.decodeResource(getResources(),
                                        R.drawable.powerup_button), getString(R.string.powerup));
                        startActivityForResult(intent, SEND_GIFT_CODE);
                        break;
                    case 1:
                        intent = Games.Requests.getSendIntent(getApiClient(), GameRequest.TYPE_GIFT,
                                "u".getBytes(), Requests.REQUEST_DEFAULT_LIFETIME_DAYS, BitmapFactory.decodeResource(getResources(),
                                        R.drawable.undo_button), getString(R.string.undo));
                        startActivityForResult(intent, SEND_GIFT_CODE);
                        break;
                }
            }
        });
        builder.create().show();
    }

    protected void checkIfQuestActive() {
        PendingResult<Quests.LoadQuestsResult> s = Games.Quests.load(getApiClient(),
                new int[]{Games.Quests.SELECT_OPEN, Quests.SELECT_ACCEPTED, Quests.SELECT_COMPLETED_UNCLAIMED},
                Quests.SORT_ORDER_ENDING_SOON_FIRST, false);

        s.setResultCallback(new ResultCallback<Quests.LoadQuestsResult>() {
            @Override
            public void onResult(Quests.LoadQuestsResult loadQuestsResult) {
                setQuestButtonEnabled(loadQuestsResult.getQuests().getCount() > 0);
            }
        });
    }

    protected void checkPendingPlayGifts() {
        PendingResult<Requests.LoadRequestsResult> pendingGifts = Games.Requests.loadRequests(getApiClient(), Requests.REQUEST_DIRECTION_INBOUND,
                GameRequest.TYPE_GIFT, Requests.SORT_ORDER_EXPIRING_SOON_FIRST);
        pendingGifts.setResultCallback(new ResultCallback<Requests.LoadRequestsResult>() {
            @Override
            public void onResult(Requests.LoadRequestsResult loadRequestsResult) {
                final ImageButton inboxButton = (ImageButton) findViewById(R.id.inbox_button);

                if (loadRequestsResult.getRequests(GameRequest.TYPE_GIFT).getCount() > 0) {
                    if(inboxButton.getVisibility() == View.VISIBLE)
                        return;

                    inboxButton.setVisibility(View.VISIBLE);

                    inboxButton.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {

                            switch(event.getAction()){
                                case MotionEvent.ACTION_UP:
                                    inboxButton.setImageResource(R.drawable.inbox_button);
                                    break;
                                case MotionEvent.ACTION_DOWN:
                                    inboxButton.setImageResource(R.drawable.inbox_button_pressed);
                                    break;
                            }

                            return false;
                        }
                    });

                    ObjectAnimator scaleX = ObjectAnimator.ofFloat(inboxButton, View.SCALE_X, 0.90f);
                    ObjectAnimator scaleY = ObjectAnimator.ofFloat(inboxButton, View.SCALE_Y, 0.90f);

                    scaleX.setDuration(1000);
                    scaleY.setDuration(1000);
                    scaleX.setRepeatCount(ObjectAnimator.INFINITE);
                    scaleX.setRepeatMode(ObjectAnimator.REVERSE);
                    scaleY.setRepeatCount(ObjectAnimator.INFINITE);
                    scaleY.setRepeatMode(ObjectAnimator.REVERSE);

                    AnimatorSet scaleDown = new AnimatorSet();
                    scaleDown.play(scaleX).with(scaleY);
                    scaleDown.start();
                }
                else
                    inboxButton.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onQuestCompleted(Quest quest) {

        Toast.makeText(this, getString(R.string.quest_completed), Toast.LENGTH_SHORT).show();

        // Claim the quest reward.
        Games.Quests.claim(this.getApiClient(), quest.getQuestId(),
                quest.getCurrentMilestone().getMilestoneId());

        // Process the RewardData to provision a specific reward.
        try {
            // The reward will be in the form [integer][character]
            // The integer is the number of items gained
            // The character is either u or p for undos or powerups
            String rewardRaw = new
                    String(quest.getCurrentMilestone().getCompletionRewardData(), "UTF-8");

            int rewardAmount = Character.getNumericValue(rewardRaw.charAt(0));
            String reward = "";

            if(rewardRaw.charAt(1) == 'p') {
                reward = "Powerup";
                incrementPowerupInventory(rewardAmount);
            }

            else {
                if(rewardRaw.charAt(1) == 'u') {
                    reward = "Undo";
                    incrementUndoInventory(rewardAmount);
                }
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Quest Completed");
            builder.setMessage("You gained " + rewardAmount + " " + reward);
            builder.create().show();

            animateFlyingTiles(150, 10);

        } catch (Exception e) {
            Toast.makeText(this, "Unable to claim quest reward", Toast.LENGTH_LONG).show();
            Log.w(LOG_TAG, e.toString());
        }
    }

    @Override
    public void onSignInFailed() {

    }

    @Override
    public void onSignInSucceeded() {
        // The sign in button is not a normal button, so keep it as a default view
        View signInButton = findViewById(R.id.sign_in_button);

        // If the user has switched views before the sign in failed then the buttons
        // are null and this will cause an error
        if(signInButton != null)
            signInButton.setVisibility(View.GONE);

        /*
        Button signOutButton = (Button) findViewById(R.id.sign_out_button);
        if(signOutButton != null)
            signOutButton.setVisibility(View.VISIBLE);
            */

        // Start the Quest listener.
        Games.Quests.registerQuestUpdateListener(this.getApiClient(), this);
    }

    private void handleInboxResult(ArrayList<GameRequest> gameRequests) {
        for(GameRequest request : gameRequests) {
            String senderName = request.getSender().getDisplayName();
            String message;

            if(new String(request.getData()).equals("p")) {

                animateFlyingTiles(150, 10);

                message = String.format(getString(R.string.powerup_gift_received), senderName);
                try {
                    incrementPowerupInventory(1);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
            else {

                animateFlyingTiles(150, 10);

                message = String.format(getString(R.string.undo_gift_received), senderName);
                try {
                    incrementUndoInventory(1);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

            Games.Requests.acceptRequest(getApiClient(), request.getRequestId());
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void incrementPowerupInventory(int amount) throws IOException, ClassNotFoundException {
        File gameDataFile = new File(getFilesDir(), getString(R.string.file_game_stats));
        GameData gameData = (GameData) Save.load(gameDataFile);
        gameData.incrementPowerupInventory(amount);
        Save.save(gameData, gameDataFile);
        updateInventoryTextView();
    }

    private void incrementUndoInventory(int amount) throws IOException, ClassNotFoundException {
        File gameDataFile = new File(getFilesDir(), getString(R.string.file_game_stats));
        GameData gameData = (GameData) Save.load(gameDataFile);
        gameData.incrementUndoInventory(amount);
        Save.save(gameData, gameDataFile);
        updateInventoryTextView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SEND_REQUEST_CODE:
                if (resultCode == GamesActivityResultCodes.RESULT_SEND_REQUEST_FAILED) {
                    Toast.makeText(this, R.string.error_send_request, Toast.LENGTH_LONG).show();
                }
                break;
            case SEND_GIFT_CODE:
                if (resultCode == GamesActivityResultCodes.RESULT_SEND_REQUEST_FAILED) {
                    Toast.makeText(this, getString(R.string.error_send_gift), Toast.LENGTH_LONG).show();
                }
                break;
            case SHOW_INBOX:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    handleInboxResult(Games.Requests
                            .getGameRequestsFromInboxResponse(data));
                } else {
                    // handle failure to process inbox result
                    if(resultCode != Activity.RESULT_CANCELED)
                        Toast.makeText(this, getString(R.string.error_claim_gift), Toast.LENGTH_LONG).show();
                }
                break;
            case QUEST_CODE:
                //Toast.makeText(this, "Quest code", Toast.LENGTH_LONG).show();
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Quest quest = data.getParcelableExtra(Quests.EXTRA_QUEST);

                    if(quest.getState() == Quest.STATE_COMPLETED)
                        onQuestCompleted(quest);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // This snippet hides the system bars.
    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // This snippet shows the system bars. It does this by removing all the flags
// except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            final ImageButton achievementsButton = (ImageButton) rootView.findViewById(R.id.achievements_button);
            final ImageButton leaderboardsButton = (ImageButton) rootView.findViewById(R.id.leaderboards_button);
            final ImageButton giftsButton = (ImageButton) rootView.findViewById(R.id.gifts_button);
            final ImageButton questsButton = (ImageButton) rootView.findViewById(R.id.quests_button);
            ImageButton themesButton = (ImageButton) rootView.findViewById(R.id.themes_button);


            achievementsButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        achievementsButton.setImageResource(R.drawable.games_achievements_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        achievementsButton.setImageResource(R.drawable.games_achievements);
                        ((MainActivity)getActivity()).playGames(view);
                    }
                    return true;
                }
            });

            leaderboardsButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        leaderboardsButton.setImageResource(R.drawable.games_leaderboards_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        leaderboardsButton.setImageResource(R.drawable.games_leaderboards);
                        ((MainActivity)getActivity()).playGames(view);
                    }
                    return true;
                }
            });

            giftsButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        giftsButton.setImageResource(R.drawable.games_gifts_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        giftsButton.setImageResource(R.drawable.games_gifts);
                        ((MainActivity)getActivity()).playGames(view);
                    }
                    return true;
                }
            });

            questsButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        questsButton.setImageResource(R.drawable.games_quests_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        questsButton.setImageResource(R.drawable.games_quests);
                        ((MainActivity)getActivity()).playGames(view);
                    }
                    return true;
                }
            });


            rootView.findViewById(R.id.single_player_imagebutton).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        view.setBackgroundResource(R.drawable.single_player_icon_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        view.setBackgroundResource(R.drawable.single_player_icon);
                        ((MainActivity)getActivity()).onClick(view);
                    }
                    return true;
                }
            });
            rootView.findViewById(R.id.multiplayer_imagebutton).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        view.setBackgroundResource(R.drawable.multiplayer_icon_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        view.setBackgroundResource(R.drawable.multiplayer_icon);
                        ((MainActivity)getActivity()).onClick(view);
                    }
                    return true;
                }
            });
            rootView.findViewById(R.id.help_button).setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        view.setBackgroundResource(R.drawable.help_button_pressed);
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        view.setBackgroundResource(R.drawable.help_button);
                        ((MainActivity)getActivity()).onClick(view);
                    }
                    return true;
                }
            });

            ImageButton settingsButton = (ImageButton) rootView.findViewById(R.id.settings_button);
            settingsButton.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        view.setBackgroundResource(R.drawable.settings_button_pressed);
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        view.setBackgroundResource(R.drawable.settings_button);
                        ((MainActivity) getActivity()).onClick(view);
                    }
                    return true;
                }
            });

            themesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MainActivity)getActivity()).showThemesDialog();
                }
            });

            ImageView appLogo = (ImageView) rootView.findViewById(R.id.logo_imageview);
            appLogo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ((MainActivity) getActivity()).onClick(view);
                }
            });

            animateSettingsButton(settingsButton);

            return rootView;
        }

        private void animateSettingsButton(ImageButton settingsButton) {
            ObjectAnimator spinAnimation = ObjectAnimator.ofFloat(settingsButton, View.ROTATION, 360);

            spinAnimation.setDuration(7000);
            spinAnimation.setRepeatMode(Animation.RESTART);
            spinAnimation.setRepeatCount(Animation.INFINITE);
            spinAnimation.setInterpolator(new LinearInterpolator());

            spinAnimation.start();
        }
    }
}
