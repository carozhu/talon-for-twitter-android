package com.klinker.android.twitter_l.activities.profile_viewer;

import android.app.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SearchRecentSuggestions;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.*;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;

import com.bumptech.glide.Glide;
import com.klinker.android.sliding.MultiShrinkScroller;
import com.klinker.android.sliding.SlidingActivity;
import com.klinker.android.twitter_l.R;
import com.klinker.android.twitter_l.views.TweetView;
import com.klinker.android.twitter_l.data.sq_lite.FavoriteUsersDataSource;
import com.klinker.android.twitter_l.data.sq_lite.FollowersDataSource;
import com.klinker.android.twitter_l.activities.media_viewer.PhotoPagerActivity;
import com.klinker.android.twitter_l.activities.media_viewer.PhotoViewerActivity;
import com.klinker.android.twitter_l.views.popups.profile.*;
import com.klinker.android.twitter_l.views.widgets.FontPrefTextView;
import com.klinker.android.twitter_l.services.TalonPullNotificationService;
import com.klinker.android.twitter_l.settings.AppSettings;
import com.klinker.android.twitter_l.activities.compose.ComposeActivity;
import com.klinker.android.twitter_l.views.widgets.FontPrefEditText;
import com.klinker.android.twitter_l.activities.compose.ComposeDMActivity;
import com.klinker.android.twitter_l.utils.IOUtils;
import com.klinker.android.twitter_l.utils.MySuggestionsProvider;
import com.klinker.android.twitter_l.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

import com.klinker.android.twitter_l.utils.text.TextUtils;
import com.yalantis.ucrop.UCrop;

import de.hdodenhof.circleimageview.CircleImageView;
import twitter4j.*;

public class ProfilePager extends SlidingActivity {

    private static final long NETWORK_ACTION_DELAY = 200;

    private Context context;
    private AppSettings settings;
    private android.support.v7.app.ActionBar actionBar;
    private SharedPreferences sharedPrefs;

    private boolean isBlocking;
    private boolean isFollowing;
    private boolean followingYou;
    private boolean isFavorite;
    private boolean isMuted;
    private boolean isRTMuted;
    private boolean isMuffled;
    private boolean isFollowingSet = false;

    @Override
    public void init(Bundle savedInstanceState) {

        Utils.setTaskDescription(this);

        context = this;
        sharedPrefs = AppSettings.getSharedPreferences(context);

        settings = AppSettings.getInstance(this);

        setPrimaryColors(
                settings.themeColors.primaryColor,
                settings.themeColors.primaryColorDark
        );


        if (!sharedPrefs.getBoolean("knows_about_profile_swipedown", false)) {
            sharedPrefs.edit().putBoolean("knows_about_profile_swipedown", true).apply();
            Snackbar.make(findViewById(android.R.id.content), R.string.tell_about_swipe_down, Snackbar.LENGTH_LONG).show();
        }

        Utils.setSharedContentTransition(this);

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if(menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception ex) {
            // Ignore
        }

        setUpTheme();
        getFromIntent();
        setContent(R.layout.user_profile);
        setHeaderContent(R.layout.sliding_profile_header);
        setUpContent();
        getUser();
        enableFullscreen();

        /*WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screenHeight = size.y;
        int screenWidth = size.x;

        Intent intent = getIntent();

        if (getIntent().getBooleanExtra(TweetActivity.USE_EXPANSION, false)) {
            enableFullscreen();
        }
        
        expandFromPoints(
                intent.getIntExtra(TweetActivity.EXPANSION_DIMEN_LEFT_OFFSET, 0),
                intent.getIntExtra(TweetActivity.EXPANSION_DIMEN_TOP_OFFSET, screenHeight),
                intent.getIntExtra(TweetActivity.EXPANSION_DIMEN_WIDTH, screenWidth),
                intent.getIntExtra(TweetActivity.EXPANSION_DIMEN_HEIGHT, 0)
        );*/
    }

    @Override
    protected void configureScroller(MultiShrinkScroller scroller) {
        super.configureScroller(scroller);
        scroller.setIntermediateHeaderHeightRatio(.75f);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        recreate();
    }

    public ImageView profilePic;
    public FontPrefTextView followerCount;
    public FontPrefTextView followingCount;
    public FontPrefTextView description;
    public FontPrefTextView location;
    public FontPrefTextView website;
    public ImageView[] friends = new ImageView[3];
    public ImageView[] followers = new ImageView[3];
    public View profileCounts;

    public void setTransitionNames() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            profilePic.setTransitionName("pro_pic");
        }
    }

    public void setUpContent() {
        // first get all the views we need
        profilePic = (ImageView) findViewById(R.id.profile_pic);

        followerCount = (FontPrefTextView) findViewById(R.id.followers_number);
        followingCount = (FontPrefTextView) findViewById(R.id.following_number);
        description = (FontPrefTextView) findViewById(R.id.user_description);
        location = (FontPrefTextView) findViewById(R.id.user_location);
        website = (FontPrefTextView) findViewById(R.id.user_webpage);
        profileCounts = findViewById(R.id.profile_counts);

        friends[0] = (ImageView) findViewById(R.id.friend_1);
        friends[1] = (ImageView) findViewById(R.id.friend_2);
        friends[2] = (ImageView) findViewById(R.id.friend_3);

        followers[0] = (ImageView) findViewById(R.id.follower_1);
        followers[1] = (ImageView) findViewById(R.id.follower_2);
        followers[2] = (ImageView) findViewById(R.id.follower_3);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            setTransitionNames();
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                loadProfilePicture();
            }
        }, 300);

        setFab(settings.themeColors.accentColor, R.drawable.ic_fab_pencil, new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    private boolean loaded = false;

    public void loadProfilePicture() {

        if (loaded || android.text.TextUtils.isEmpty(proPic)) {
            return;
        }

        try {
            Glide.with(this)
                    .load(proPic)
                    .into((CircleImageView) findViewById(R.id.profile_image));
        } catch (Exception e) {

        }

        findViewById(R.id.photo_touch_intercept_overlay).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (thisUser != null) {
                    PhotoPagerActivity.startActivity(context, 0, proPic + " " + thisUser.getProfileBannerURL(), 0);
                } else {
                    PhotoViewerActivity.startActivity(context, proPic);
                }
            }
        });
    }

    public void setUpTheme() {
        Utils.setUpProfileTheme(context, settings);
    }

    private boolean isMyProfile = false;
    private String name;
    private String screenName;
    private String proPic;
    private long tweetId;
    private boolean isRetweet;

    public void getFromIntent() {
        Intent from = getIntent();

        name = from.getStringExtra("name");
        screenName = from.getStringExtra("screenname");
        proPic = from.getStringExtra("proPic");
        tweetId = from.getLongExtra("tweetid", 0l);
        isRetweet = from.getBooleanExtra("retweet", false);

        if (screenName != null && screenName.equalsIgnoreCase(settings.myScreenName)) {
            isMyProfile = true;
        }
    }

    public TextView followText;
    public TextView favoriteText;

    public void setProfileCard(final User user) {

        if (android.text.TextUtils.isEmpty(proPic)) {
            proPic = user.getOriginalProfileImageURL();
            name = user.getName();

            loadProfilePicture();
        }

        setTitle(user.getName());

        CardView headerCard = (CardView) findViewById(R.id.stats_card);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) headerCard.getLayoutParams();
        params.topMargin = Utils.toDP(32, context);
        headerCard.setLayoutParams(params);

        setFab(settings.themeColors.accentColor, R.drawable.ic_fab_pencil, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent compose = new Intent(context, ComposeActivity.class);
                ActivityOptions opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                        v.getMeasuredWidth(), v.getMeasuredHeight());
                compose.putExtra("user", "@" + screenName);
                compose.putExtra("already_animated", true);
                startActivity(compose, opts.toBundle());
            }
        });

        String des = user.getDescription();
        String loc = user.getLocation();
        String web = user.getURL();

        if (des != null && !des.equals("")) {
            description.setText(des);
        } else {
            description.setVisibility(View.GONE);
        }
        if (loc != null && !loc.equals("")) {
            location.setText(loc);
        } else {
            location.setVisibility(View.GONE);
        }
        if (web != null && !web.equals("")) {
            website.setText(user.getURLEntity().getDisplayURL());
            TextUtils.linkifyText(context, website, null, true, user.getURLEntity().getExpandedURL(), false);

            if (location.getVisibility() == View.GONE) {
                website.setPadding(0, Utils.toDP(16, context), 0, 0);
            }
        } else {
            website.setVisibility(View.GONE);
        }

        TextUtils.linkifyText(context, description, null, true, "", false);

        TextView followingStatus = (TextView) findViewById(R.id.follow_status);
        followText = (TextView) findViewById(R.id.follow_button_text);
        favoriteText = (TextView) findViewById(R.id.favorite_button);
        LinearLayout followButton = (LinearLayout) findViewById(R.id.follow_button);

        if (isFollowing) {
            followText.setText(getString(R.string.menu_unfollow));
        } else {
            followText.setText(getString(R.string.menu_follow));
        }

        if (isFollowing || !settings.crossAccActions) {
            followButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    new FollowUser(TYPE_ACC_ONE).execute();
                }
            });
        } else if (settings.crossAccActions) {
            followButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // dialog for favoriting
                    String[] options = new String[3];

                    options[0] = "@" + settings.myScreenName;
                    options[1] = "@" + settings.secondScreenName;
                    options[2] = context.getString(R.string.both_accounts);

                    new android.app.AlertDialog.Builder(context)
                            .setItems(options, new DialogInterface.OnClickListener() {
                                public void onClick(final DialogInterface dialog, final int item) {
                                    new FollowUser(item + 1).execute();
                                }
                            })
                            .create().show();
                }
            });
        }

        if (followingYou) {
            followingStatus.setText(getString(R.string.follows_you));
        } else {
            followingStatus.setText(getString(R.string.not_following_you));
        }

        if (isFavorite) {
            favoriteText.setText(getString(R.string.menu_unfavorite));
        } else {
            favoriteText.setText(getString(R.string.menu_favorite));
        }

        favoriteText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new FavoriteUser().execute();
            }
        });

        if (user.getScreenName().equals(settings.myScreenName)) {
            // they are you
            findViewById(R.id.header_button_section).setVisibility(View.GONE);
        }

        Button pictures = (Button) findViewById(R.id.pictures_button);
        pictures.setTextColor(settings.themeColors.primaryColorLight);

        picsPopup = new PicturesPopup(context, thisUser);
        pictures.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                picsPopup.setExpansionPointForAnim(view);
                picsPopup.show();
            }
        });

        if (user.getFriendsCount() < 1000) {
            followingCount.setText(getString(R.string.following) + ": " + user.getFriendsCount());
        } else {
            followingCount.setText(getString(R.string.following) + ": " + Utils.coolFormat(user.getFriendsCount(), 0));
        }
        if (user.getFollowersCount() < 1000) {
            followerCount.setText(getString(R.string.followers) + ": " + user.getFollowersCount());
        } else {
            followerCount.setText(getString(R.string.followers) + ": " + Utils.coolFormat(user.getFollowersCount(),0));
        }

        TextView statsTitle = (TextView) findViewById(R.id.stats_title_text);

        statsTitle.setTextColor(settings.themeColors.primaryColorLight);
        statsTitle.setText("@" + user.getScreenName());

        ImageView verified = (ImageView) findViewById(R.id.verified);
        if (settings.darkTheme) {
            verified.setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
        } else {
            verified.setColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY);
        }

        FontPrefTextView createdAt = (FontPrefTextView) findViewById(R.id.created_at);
        FontPrefTextView listsCount = (FontPrefTextView) findViewById(R.id.number_of_lists);

        if (user.isVerified()) {
            verified.setVisibility(View.VISIBLE);
        }

        SimpleDateFormat ft = new SimpleDateFormat("MMM dd, yyyy");

        createdAt.setText(getString(R.string.joined_twitter) +" " + ft.format(user.getCreatedAt()));

        if (user.getListedCount() == 0) {
            listsCount.setVisibility(View.GONE);
        } else {
            listsCount.setText(getString(R.string.list_count).replace("%s", user.getListedCount() + ""));
        }

        View openFollowers = findViewById(R.id.view_followers);
        fol = new ProfileFollowersPopup(context, user);

        openFollowers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fol.setExpansionPointForAnim(view);
                fol.setOnTopOfView(view);
                fol.show();
            }
        });
        openFollowers.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ProfilePager.this,
                        getString(R.string.followers) + ": " + user.getFollowersCount(),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        View openFriends = findViewById(R.id.view_friends);
        fri = new ProfileFriendsPopup(context, user);
        openFriends.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fri.setExpansionPointForAnim(view);
                fri.setOnTopOfView(view);
                fri.show();
            }
        });
        openFriends.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ProfilePager.this,
                        getString(R.string.following) + ": " + user.getFriendsCount(),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        showCard(findViewById(R.id.stats_card));
    }

    private PicturesPopup picsPopup;
    private ProfileFollowersPopup fol;
    private ProfileFriendsPopup fri;

    private void showStats(final User user) {


        //showCard(findViewById(R.id.stats_card));
    }

    public List<Status> tweets = new ArrayList<Status>();
    public ProfileTweetsPopup tweetsPopup;
    private void showTweets() {
        TextView tweetsTitle = (TextView) findViewById(R.id.tweets_title_text);
        Button showAllTweets = (Button) findViewById(R.id.show_all_tweets_button);
        final LinearLayout content = (LinearLayout) findViewById(R.id.tweets_content);

        final View tweetsLayout = getLayoutInflater().inflate(R.layout.convo_popup_layout, null, false);

        if (tweetsTitle == null) {
            return;
        }

        tweetsTitle.setTextColor(settings.themeColors.primaryColorLight);
        showAllTweets.setTextColor(settings.themeColors.primaryColorLight);

        tweetsPopup = new ProfileTweetsPopup(context, tweetsLayout, thisUser);

        showAllTweets.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tweetsPopup.setExpansionPointForAnim(view);

                tweetsPopup.show();
            }
        });
        showAllTweets.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ProfilePager.this,
                        getString(R.string.tweets) + ": " + thisUser.getStatusesCount(),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        if (thisUser.getStatusesCount() < 1000) {
            showAllTweets.setText(getString(R.string.show_all) + " (" + thisUser.getStatusesCount() + ")");
        } else {
            showAllTweets.setText(getString(R.string.show_all) + " (" + Utils.coolFormat(thisUser.getStatusesCount(), 0) + ")");
        }

        int size = 0;
        if (tweets.size() >= 3) {
            size = 3;
        } else {
            size = tweets.size();
        }

        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    View tweetDivider = new View(context);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.toDP(1, context));
                    tweetDivider.setLayoutParams(params);

                    if (settings.darkTheme) {
                        tweetDivider.setBackgroundColor(getResources().getColor(R.color.dark_text_drawer));
                    } else {
                        tweetDivider.setBackgroundColor(getResources().getColor(R.color.light_text_drawer));
                    }

                    content.addView(tweetDivider);
                }

                TweetView t = new TweetView(context, tweets.get(i));
                t.setCurrentUser(thisUser.getScreenName());
                t.setSmallImage(true);
                content.addView(t.getView());
            }
        } else {
            // add a no tweets textbox
        }

        showCard(findViewById(R.id.tweets_card));
    }

    public List<Status> mentions = new ArrayList<Status>();
    public ProfileMentionsPopup mentionsPopup;
    private void showMentions() {
        TextView mentionsTitle = (TextView) findViewById(R.id.mentions_title_text);
        Button showAllMentions = (Button) findViewById(R.id.show_all_mentions_button);
        LinearLayout content = (LinearLayout) findViewById(R.id.mentions_content);

        final View mentionsLayout = getLayoutInflater().inflate(R.layout.convo_popup_layout, null, false);

        if (mentionsTitle == null) {
            return;
        }

        mentionsTitle.setTextColor(settings.themeColors.primaryColorLight);
        showAllMentions.setTextColor(settings.themeColors.primaryColorLight);

        mentionsPopup = new ProfileMentionsPopup(context, mentionsLayout, thisUser);

        showAllMentions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mentionsPopup.setExpansionPointForAnim(view);
                mentionsPopup.show();
            }
        });

        int size = 0;
        if (mentions.size() >= 3) {
            size = 3;
        } else {
            size = mentions.size();
        }

        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    View tweetDivider = new View(context);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.toDP(1, context));
                    tweetDivider.setLayoutParams(params);

                    if (settings.darkTheme) {
                        tweetDivider.setBackgroundColor(getResources().getColor(R.color.dark_text_drawer));
                    } else {
                        tweetDivider.setBackgroundColor(getResources().getColor(R.color.light_text_drawer));
                    }

                    content.addView(tweetDivider);
                }

                TweetView t = new TweetView(context, mentions.get(i));
                t.setCurrentUser(thisUser.getScreenName());
                t.setSmallImage(true);
                content.addView(t.getView());
            }
        } else {
            // add a no mentions textbox
        }

        if (mentions.size() > 0) {
            showCard(findViewById(R.id.mentions_card));
        } else {
            findViewById(R.id.mentions_card).setVisibility(View.GONE);
        }
    }

    public List<Status> favorites = new ArrayList<Status>();
    public ProfileFavoritesPopup favoritesPopup;
    private void showFavorites() {
        TextView favoritesTitle = (TextView) findViewById(R.id.favorites_title_text);
        Button showAllfavorites = (Button) findViewById(R.id.show_all_favorites_button);
        LinearLayout content = (LinearLayout) findViewById(R.id.favorites_content);

        final View favoritesLayout = getLayoutInflater().inflate(R.layout.convo_popup_layout, null, false);

        if (favoritesTitle == null) {
            return;
        }

        favoritesTitle.setTextColor(settings.themeColors.primaryColorLight);
        showAllfavorites.setTextColor(settings.themeColors.primaryColorLight);

        favoritesPopup = new ProfileFavoritesPopup(context, favoritesLayout, thisUser);

        showAllfavorites.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                favoritesPopup.setExpansionPointForAnim(view);
                favoritesPopup.show();
            }
        });

        int size = 0;
        if (favorites.size() >= 3) {
            size = 3;
        } else {
            size = favorites.size();
        }

        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    View tweetDivider = new View(context);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Utils.toDP(1, context));
                    tweetDivider.setLayoutParams(params);

                    if (settings.darkTheme) {
                        tweetDivider.setBackgroundColor(getResources().getColor(R.color.dark_text_drawer));
                    } else {
                        tweetDivider.setBackgroundColor(getResources().getColor(R.color.light_text_drawer));
                    }

                    content.addView(tweetDivider);
                }

                TweetView t = new TweetView(context, favorites.get(i));
                t.setCurrentUser(thisUser.getScreenName());
                t.setSmallImage(true);
                content.addView(t.getView());
            }
        } else {
            // add a no favorites textbox
        }

        if (favorites.size() > 0) {
            showCard(findViewById(R.id.favorites_card));
        } else {
            findViewById(R.id.favorites_card).setVisibility(View.GONE);
        }
    }

    private View spinner;
    private void showCard(final View v) {
        if (spinner == null) {
            spinner = findViewById(R.id.spinner);
        }
        if (spinner.getVisibility() == View.VISIBLE) {
            Animation anim = AnimationUtils.loadAnimation(context, R.anim.fade_out);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (spinner.getVisibility() != View.GONE) {
                        spinner.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            anim.setDuration(250);
            spinner.startAnimation(anim);
        }

        Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_card_up);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (v.getVisibility() != View.VISIBLE) {
                    v.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        anim.setStartOffset(150);
        anim.setDuration(450);
        v.startAnimation(anim);
    }

    public User thisUser;

    public void getUser() {
        Thread getUser = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(NETWORK_ACTION_DELAY);
                } catch (Exception e) {

                }

                Twitter twitter =  Utils.getTwitter(context, settings);

                try {
                    thisUser = twitter.showUser(screenName);
                } catch (Exception e) {
                    thisUser = null;
                }

                if (thisUser == null) {
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, R.string.error, Toast.LENGTH_SHORT).show();

                            if (spinner == null) {
                                spinner = findViewById(R.id.spinner);
                            }
                            if (spinner.getVisibility() == View.VISIBLE) {
                                spinner.startAnimation(AnimationUtils.loadAnimation(context, R.anim.fade_out));
                                spinner.setVisibility(View.GONE);
                            }
                        }
                    });
                }

                try {
                    FollowersDataSource.getInstance(context).createUser(thisUser, sharedPrefs.getInt("current_account", 1));

                } catch (Exception e) {
                    // the user already exists. don't know if this is more efficient than querying the db or not.
                }

                if (thisUser != null) {
                    final SearchRecentSuggestions suggestions = new SearchRecentSuggestions(context,
                            MySuggestionsProvider.AUTHORITY, MySuggestionsProvider.MODE);
                    suggestions.saveRecentQuery("@" + thisUser.getScreenName(), null);
                }

                // set the info to set up the action bar items
                if (isMyProfile) {
                    if (thisUser != null) {
                        // put in the banner and profile pic to shared prefs
                        sharedPrefs.edit().putString("profile_pic_url_" + settings.currentAccount, thisUser.getOriginalProfileImageURL()).apply();
                        sharedPrefs.edit().putString("twitter_background_url_" + settings.currentAccount, thisUser.getProfileBannerURL()).apply();
                        isMuffled = sharedPrefs.getStringSet("muffled_users", new HashSet<String>()).contains(screenName);
                        isMuted = sharedPrefs.getString("muted_users", "").contains(screenName);
                        isRTMuted = sharedPrefs.getString("muted_rts", "").contains(screenName);
                    }
                } else {
                    try {

                        String otherUserName = screenName;
                        Relationship friendship = twitter.showFriendship(settings.myScreenName, otherUserName);

                        isFollowing = friendship.isSourceFollowingTarget();
                        followingYou = friendship.isTargetFollowingSource();
                        isBlocking = friendship.isSourceBlockingTarget();
                        isMuted = sharedPrefs.getString("muted_users", "").contains(screenName);
                        isRTMuted = sharedPrefs.getString("muted_rts", "").contains(screenName);
                        isMuffled = sharedPrefs.getStringSet("muffled_users", new HashSet<String>()).contains(screenName);
                        isFavorite = FavoriteUsersDataSource.getInstance(context).isFavUser(otherUserName);

                        isFollowingSet = true;

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (thisUser != null) {
                    ((Activity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //actionBar.setTitle(thisUser.getName());
                            invalidateOptionsMenu();
                            setProfileCard(thisUser);
                            showStats(thisUser);

                            try {
                                Glide.with(context)
                                        .load(thisUser.getProfileBannerURL())
                                        .centerCrop()
                                        .into((ImageView) findViewById(R.id.background_image));
                            } catch (Exception e) {

                            }
                        }
                    });
                }

                // start the other actions now that we are done finding the user
                getFollowers(twitter);
                getFriends(twitter);

                // if they aren't protected, then get their tweets, favorites, etc.
                try {
                    try {
                        Thread.sleep(NETWORK_ACTION_DELAY);
                    } catch (Exception e) {

                    }

                    // tweets first
                    // this will error out if they are protected and we can't reach them
                    tweets = twitter.getUserTimeline(thisUser.getId(), new Paging(1, 20));

                    ((Activity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showTweets();
                        }
                    });

                    try {
                        Thread.sleep(NETWORK_ACTION_DELAY);
                    } catch (Exception e) {

                    }

                    getMentions(twitter);
                    ((Activity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showMentions();
                        }
                    });

                    try {
                        Thread.sleep(NETWORK_ACTION_DELAY);
                    } catch (Exception e) {

                    }

                    favorites = twitter.getFavorites(thisUser.getId(), new Paging(1, 3));
                    ((Activity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFavorites();
                        }
                    });
                    getPictures(twitter);
                } catch (Exception e) {
                    if (thisUser != null && thisUser.isProtected()) {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, getString(R.string.protected_account), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, getString(R.string.rate_limit_reached), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }

            }
        });

        getUser.setPriority(Thread.MAX_PRIORITY);
        getUser.start();
    }

    private void getMentions(Twitter twitter) {
        try {
            Query query = new Query("@" + screenName + " -RT");
            query.sinceId(1);
            QueryResult result = twitter.search(query);

            mentions.clear();

            for (twitter4j.Status status : result.getTweets()) {
                mentions.add(status);
            }

            while (result.hasNext() && mentions.size() < 3) {
                query = result.nextQuery();
                result = twitter.search(query);

                for (twitter4j.Status status : result.getTweets()) {
                    mentions.add(status);
                }
            }

        } catch (Throwable t) {

        }


    }

    private void getPictures(Twitter twitter) {

    }

    private void getFollowers(Twitter twitter) {

        try {
            Thread.sleep(NETWORK_ACTION_DELAY);
        } catch (Exception e) {

        }

        try {
            final List<User> followers = twitter.getFollowersList(thisUser.getId(), -1, 3);

            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setFollowers(followers);
                }
            });
        } catch (Exception e) {

        }
    }

    private void setFollowers(List<User> followers) {
        switch (followers.size()) {
            case 0:
                for(int i = 0; i < 3; i++)
                    this.followers[i].setVisibility(View.INVISIBLE);
                break;
            case 1:
                for(int i = 0; i < 2; i++)
                    this.followers[i].setVisibility(View.INVISIBLE);
                glide(followers.get(0).getBiggerProfileImageURL(), this.followers[2]);
                break;
            case 2:
                for(int i = 0; i < 1; i++)
                    this.followers[i].setVisibility(View.INVISIBLE);
                glide(followers.get(0).getBiggerProfileImageURL(), this.followers[1]);
                glide(followers.get(1).getBiggerProfileImageURL(), this.followers[2]);
                break;
            case 3:
                glide(followers.get(0).getBiggerProfileImageURL(), this.followers[0]);
                glide(followers.get(1).getBiggerProfileImageURL(), this.followers[1]);
                glide(followers.get(2).getBiggerProfileImageURL(), this.followers[2]);
                break;
        }
    }

    private void getFriends(Twitter twitter) {
        try {
            Thread.sleep(NETWORK_ACTION_DELAY);
        } catch (Exception e) {

        }

        try {
            final List<User> friends = twitter.getFriendsList(thisUser.getId(), -1, 3);

            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setFriends(friends);
                }
            });
        } catch (Exception e) {

        }
    }

    private void setFriends(List<User> friends) {
        switch (friends.size()) {
            case 0:
                for(int i = 0; i < 3; i++) // 0, 1, and 2 are gone
                    this.friends[i].setVisibility(View.INVISIBLE);
                break;
            case 1:
                for(int i = 0; i < 2; i++) // 0 and 1 are gone
                    this.friends[i].setVisibility(View.INVISIBLE);
                glide(friends.get(0).getBiggerProfileImageURL(), this.friends[2]);
                break;
            case 2:
                this.friends[0].setVisibility(View.INVISIBLE);
                glide(friends.get(0).getBiggerProfileImageURL(), this.friends[1]);
                glide(friends.get(1).getBiggerProfileImageURL(), this.friends[2]);
                break;
            case 3:
                glide(friends.get(0).getBiggerProfileImageURL(), this.friends[0]);
                glide(friends.get(1).getBiggerProfileImageURL(), this.friends[1]);
                glide(friends.get(2).getBiggerProfileImageURL(), this.friends[2]);
                break;
        }
    }

    class GetActionBarInfo extends AsyncTask<String, Void, Void> {
        protected Void doInBackground(String... urls) {
            if (isMyProfile) {
                if (thisUser != null) {
                    // put in the banner and profile pic to shared prefs
                    sharedPrefs.edit().putString("profile_pic_url_" + sharedPrefs.getInt("current_account", 1), thisUser.getOriginalProfileImageURL()).apply();
                    sharedPrefs.edit().putString("twitter_background_url_" + sharedPrefs.getInt("current_account", 1), thisUser.getProfileBannerURL()).apply();
                }
                return null;
            } else {
                try {
                    int currentAccount = sharedPrefs.getInt("current_account", 1);
                    Twitter twitter =  Utils.getTwitter(context, settings);

                    String otherUserName = screenName;
                    Relationship friendship = twitter.showFriendship(settings.myScreenName, otherUserName);

                    isFollowing = friendship.isSourceFollowingTarget();
                    isBlocking = friendship.isSourceBlockingTarget();
                    isMuted = sharedPrefs.getString("muted_users", "").contains(screenName);
                    isRTMuted = sharedPrefs.getString("muted_rts", "").contains(screenName);
                    isFavorite = FavoriteUsersDataSource.getInstance(context).isFavUser(otherUserName);
                    isFollowingSet = true;

                    return null;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

        protected void onPostExecute(Void none) {
            if (thisUser != null) {
                //actionBar.setTitle(thisUser.getName());
            }
            invalidateOptionsMenu();
        }
    }

    private final int TYPE_ACC_ONE = 1;
    private final int TYPE_ACC_TWO = 2;
    private final int TYPE_BOTH_ACC = 3;

    class FollowUser extends AsyncTask<String, Void, Boolean> {

        private Exception e = null;

        private int followType;

        public FollowUser(int followType) {
            this.followType = followType;
        }

        protected Boolean doInBackground(String... urls) {
            try {
                if (thisUser != null) {
                    Twitter twitter = null;
                    Twitter secTwitter = null;
                    if (followType == TYPE_ACC_ONE) {
                        twitter = Utils.getTwitter(context, settings);
                    } else if (followType == TYPE_ACC_TWO) {
                        secTwitter = Utils.getSecondTwitter(context);
                    } else {
                        twitter = Utils.getTwitter(context, settings);
                        secTwitter = Utils.getSecondTwitter(context);
                    }

                    String otherUserName = thisUser.getScreenName();
                    boolean isFollowing = false;

                    if (twitter != null) {
                        Relationship friendship = twitter.showFriendship(settings.myScreenName, otherUserName);
                        isFollowing = friendship.isSourceFollowingTarget();
                    }

                    if (isFollowing) {
                        if (twitter != null) {
                            twitter.destroyFriendship(otherUserName);
                        }

                        if (secTwitter != null) {
                            secTwitter.createFriendship(otherUserName);
                        }

                        return false;
                    } else {
                        if (twitter != null) {
                            twitter.createFriendship(otherUserName);
                        }

                        if (secTwitter != null) {
                            secTwitter.createFriendship(otherUserName);
                        }

                        FollowersDataSource.getInstance(context).createUser(thisUser, sharedPrefs.getInt("current_account", 1));

                        return true;
                    }
                }

                return null;
            } catch (Exception e) {
                e.printStackTrace();
                this.e = e;
                return null;
            }
        }

        protected void onPostExecute(Boolean created) {
            // add a toast - now following or unfollowed
            // true = followed
            // false = unfollowed
            if (created != null) {
                if (created) {
                    Toast.makeText(context, getResources().getString(R.string.followed_user), Toast.LENGTH_SHORT).show();
                    followText.setText(getString(R.string.menu_unfollow));
                } else {
                    Toast.makeText(context, getResources().getString(R.string.unfollowed_user), Toast.LENGTH_SHORT).show();
                    followText.setText(getString(R.string.menu_follow));
                }
            } else {
                Toast.makeText(context, getResources().getString(R.string.error) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            if(settings.liveStreaming) {
                context.sendBroadcast(new Intent("com.klinker.android.twitter.STOP_PUSH_SERVICE"));
                context.startService(new Intent(context, TalonPullNotificationService.class));
            }

            new GetActionBarInfo().execute();
        }
    }

    class BlockUser extends AsyncTask<String, Void, Boolean> {

        protected Boolean doInBackground(String... urls) {
            try {
                if (thisUser != null) {
                    Twitter twitter =  Utils.getTwitter(context, settings);

                    String otherUserName = thisUser.getScreenName();

                    Relationship friendship = twitter.showFriendship(settings.myScreenName, otherUserName);

                    boolean isBlocking = friendship.isSourceBlockingTarget();

                    if (isBlocking) {
                        twitter.destroyBlock(otherUserName);
                        return false;
                    } else {
                        twitter.createBlock(otherUserName);
                        return true;
                    }
                }

                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(Boolean isBlocked) {
            // true = followed
            // false = unfollowed
            if (isBlocked != null) {
                if (isBlocked) {
                    Toast.makeText(context, getResources().getString(R.string.blocked_user), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, getResources().getString(R.string.unblocked_user), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }

            new GetActionBarInfo().execute();
        }
    }

    class FavoriteUser extends AsyncTask<String, Void, Boolean> {

        protected Boolean doInBackground(String... urls) {
            try {
                int currentAccount = sharedPrefs.getInt("current_account", 1);
                if (thisUser != null) {
                    if (isFavorite) {
                        // destroy favorite
                        FavoriteUsersDataSource.getInstance(context).deleteUser(thisUser.getId());

                        String favs = sharedPrefs.getString("favorite_user_names_" + currentAccount, "");
                        favs = favs.replaceAll(thisUser.getScreenName() + " ", "");
                        sharedPrefs.edit().putString("favorite_user_names_" + currentAccount, favs).apply();

                        return false;

                    } else {
                        FavoriteUsersDataSource.getInstance(context).createUser(thisUser, currentAccount);

                        sharedPrefs.edit().putString("favorite_user_names_" + currentAccount, sharedPrefs.getString("favorite_user_names_" + currentAccount, "") + thisUser.getScreenName() + " ").apply();

                        return true;
                    }
                }

                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(Boolean isFavorited) {
            // true = followed
            // false = unfollowed
            if (isFavorited != null) {
                if (isFavorited) {
                    Toast.makeText(context, getResources().getString(R.string.favorite_user), Toast.LENGTH_SHORT).show();
                    favoriteText.setText(getString(R.string.menu_unfavorite));
                } else {
                    Toast.makeText(context, getResources().getString(R.string.unfavorite_user), Toast.LENGTH_SHORT).show();
                    favoriteText.setText(getString(R.string.menu_favorite));
                }
            } else {
                Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }

            new GetActionBarInfo().execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.profile_activity, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        //final int MENU_TWEET = 0;
        final int MENU_BLOCK = 0;
        final int MENU_UNBLOCK = 1;
        final int MENU_ADD_LIST = 2;
        final int MENU_DM = 3;
        final int MENU_CHANGE_PICTURE = 4;
        final int MENU_CHANGE_BANNER = 5;
        final int MENU_CHANGE_BIO = 6;
        final int MENU_MUTE = 7;
        final int MENU_UNMUTE = 8;
        final int MENU_MUTE_RT = 9;
        final int MENU_UNMUTE_RT = 10;
        final int MENU_MUFFLE = 11;
        final int MENU_UNMUFFLE = 12;
        final int MENU_SHARE_LINK = 13;

        if (isMyProfile) {
            //menu.getItem(MENU_TWEET).setVisible(false);
            menu.getItem(MENU_BLOCK).setVisible(false);
            menu.getItem(MENU_UNBLOCK).setVisible(false);
            menu.getItem(MENU_ADD_LIST).setVisible(false);
            menu.getItem(MENU_DM).setVisible(false);
        } else {
            if (isFollowingSet) {
                if (isBlocking) {
                    menu.getItem(MENU_BLOCK).setVisible(false);
                } else {
                    menu.getItem(MENU_UNBLOCK).setVisible(false);
                }
            } else {
                menu.getItem(MENU_BLOCK).setVisible(false);
                menu.getItem(MENU_UNBLOCK).setVisible(false);
                menu.getItem(MENU_MUTE).setVisible(false);
                menu.getItem(MENU_UNMUTE).setVisible(false);
                menu.getItem(MENU_MUTE_RT).setVisible(false);
                menu.getItem(MENU_UNMUTE_RT).setVisible(false);
                menu.getItem(MENU_MUFFLE).setVisible(false);
                menu.getItem(MENU_UNMUFFLE).setVisible(false);
            }

            menu.getItem(MENU_CHANGE_BIO).setVisible(false);
            menu.getItem(MENU_CHANGE_BANNER).setVisible(false);
            menu.getItem(MENU_CHANGE_PICTURE).setVisible(false);
        }

        if (isFollowingSet || isMyProfile) {
            if (isMuffled) {
                menu.getItem(MENU_MUFFLE).setVisible(false);
            } else {
                menu.getItem(MENU_UNMUFFLE).setVisible(false);
            }
            if (isMuted) {
                menu.getItem(MENU_MUTE).setVisible(false);
            } else {
                menu.getItem(MENU_UNMUTE).setVisible(false);
            }
            if (isRTMuted) {
                menu.getItem(MENU_MUTE_RT).setVisible(false);
            } else {
                menu.getItem(MENU_UNMUTE_RT).setVisible(false);
            }
        } else {
            menu.getItem(MENU_MUFFLE).setVisible(false);
            menu.getItem(MENU_UNMUFFLE).setVisible(false);
            menu.getItem(MENU_MUTE).setVisible(false);
            menu.getItem(MENU_UNMUTE).setVisible(false);
            menu.getItem(MENU_MUTE_RT).setVisible(false);
            menu.getItem(MENU_UNMUTE_RT).setVisible(false);
            menu.getItem(MENU_UNMUTE_RT).setVisible(false);
        }

        return true;
    }

    @Override
    public void finish() {
        SharedPreferences sharedPrefs = AppSettings.getSharedPreferences(context);

        // this is used in the onStart() for the home fragment to tell whether or not it should refresh
        // tweetmarker. Since coming out of this will only call onResume(), it isn't needed.
        //sharedPrefs.edit().putBoolean("from_activity", true).apply();

        super.finish();
        overridePendingTransition(R.anim.activity_slide_up, R.anim.activity_slide_down);

        try {
            if (isMyProfile) {
                AppSettings.invalidate();
            }
        } catch (Exception e) {

        }
    }

    @Override
    public void onBackPressed() {
        if (tweetsPopup != null && tweetsPopup.isShowing()) {
            tweetsPopup.hide();
        } else if (mentionsPopup != null && mentionsPopup.isShowing()) {
            mentionsPopup.hide();
        } else if (favoritesPopup != null && favoritesPopup.isShowing()) {
            favoritesPopup.hide();
        } else if (picsPopup != null && picsPopup.isShowing()) {
            picsPopup.hide();
        } else if (fol != null && fol.isShowing()) {
            fol.hide();
        } else if (fri != null && fri.isShowing()) {
            fri.hide();
        } else {
            super.onBackPressed();
        }
    }

    private final int SELECT_PRO_PIC = 57;
    private final int SELECT_BANNER = 58;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.menu_block:
                new BlockUser().execute();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                return true;

            case R.id.menu_unblock:
                new BlockUser().execute();
                return true;

            case R.id.menu_add_to_list:
                new GetLists().execute();
                return true;

            /*case R.id.menu_tweet:
                Intent compose = new Intent(context, ComposeActivity.class);
                compose.putExtra("user", "@" + screenName);
                startActivity(compose);
                return true;*/

            case R.id.menu_dm:
                Intent dm = new Intent(context, ComposeDMActivity.class);
                dm.putExtra("screenname", screenName);
                startActivity(dm);
                return true;

            case R.id.menu_change_picture:
                Intent photoPickerIntent = new Intent();
                photoPickerIntent.setType("image/*");
                photoPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
                try {
                    startActivityForResult(Intent.createChooser(photoPickerIntent,
                            "Select Picture"), SELECT_PRO_PIC);
                } catch (Throwable t) {
                    // no app to preform this..? hmm, tell them that I guess
                    Toast.makeText(context, "No app available to select pictures!", Toast.LENGTH_SHORT).show();
                }
                return true;

            case R.id.menu_change_banner:
                Intent bannerPickerIntent = new Intent();
                bannerPickerIntent.setType("image/*");
                bannerPickerIntent.setAction(Intent.ACTION_GET_CONTENT);
                try {
                    startActivityForResult(Intent.createChooser(bannerPickerIntent,
                            "Select Picture"), SELECT_BANNER);
                } catch (Throwable t) {
                    // no app to preform this..? hmm, tell them that I guess
                    Toast.makeText(context, "No app available to select pictures!", Toast.LENGTH_SHORT).show();
                }
                return true;

            case  R.id.menu_change_bio:
                updateProfile();
                return true;

            case R.id.menu_mute:
                String current = sharedPrefs.getString("muted_users", "");
                sharedPrefs.edit().putString("muted_users", current + screenName.replaceAll(" ", "").replaceAll("@", "") + " ").apply();
                sharedPrefs.edit().putBoolean("refresh_me", true).apply();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                finish();
                return true;

            case R.id.menu_unmute:
                String muted = sharedPrefs.getString("muted_users", "");
                muted = muted.replace(screenName + " ", "");
                sharedPrefs.edit().putString("muted_users", muted).apply();
                sharedPrefs.edit().putBoolean("refresh_me", true).apply();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                finish();
                return true;

            case R.id.menu_mute_rt:
                String muted_rts = sharedPrefs.getString("muted_rts", "");
                sharedPrefs.edit().putString("muted_rts", muted_rts + screenName.replaceAll(" ", "").replaceAll("@", "") + " ").apply();
                sharedPrefs.edit().putBoolean("refresh_me", true).apply();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                finish();
                return true;

            case R.id.menu_unmute_rt:
                String curr_muted = sharedPrefs.getString("muted_rts", "");
                curr_muted = curr_muted.replace(screenName + " ", "");
                sharedPrefs.edit().putString("muted_rts", curr_muted).apply();
                sharedPrefs.edit().putBoolean("refresh_me", true).apply();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                finish();
                return true;

            case R.id.menu_muffle_user:
                Set<String> muffled = sharedPrefs.getStringSet("muffled_users", new HashSet<String>());
                muffled.add(screenName);
                sharedPrefs.edit().putStringSet("muffled_users", muffled).apply();
                sharedPrefs.edit().putBoolean("refresh_me", true).apply();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                finish();
                return true;

            case R.id.menu_unmuffle_user:
                muffled = sharedPrefs.getStringSet("muffled_users", new HashSet<String>());
                muffled.remove(screenName);
                sharedPrefs.edit().putStringSet("muffled_users", muffled).apply();
                sharedPrefs.edit().putBoolean("refresh_me", true).apply();
                sharedPrefs.edit().putBoolean("just_muted", true).apply();
                finish();
                return true;

            case R.id.menu_share_link:
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, "http://twitter.com/" + screenName.replace("@", ""));

                startActivity(Intent.createChooser(share, "Share with:"));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void updateProfile() {
        final Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.change_profile_info_dialog);
        dialog.setTitle(getResources().getString(R.string.change_profile_info) + ":");

        final FontPrefEditText name = (FontPrefEditText) dialog.findViewById(R.id.name);
        final FontPrefEditText url = (FontPrefEditText) dialog.findViewById(R.id.url);
        final FontPrefEditText location = (FontPrefEditText) dialog.findViewById(R.id.location);
        final FontPrefEditText description = (FontPrefEditText) dialog.findViewById(R.id.description);

        name.setText(thisUser.getName());

        try {
            url.setText(thisUser.getURLEntity().getDisplayURL());
        } catch (Exception e) {

        }

        location.setText(thisUser.getLocation());
        description.setText(thisUser.getDescription());

        Button cancel = (Button) dialog.findViewById(R.id.cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        Button change = (Button) dialog.findViewById(R.id.change);
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean ok = true;
                String nameS = null;
                String urlS = null;
                String locationS = null;
                String descriptionS = null;

                if(name.getText().length() <= 20 && ok) {
                    if (name.getText().length() > 0){
                        nameS = name.getText().toString();
                        sharedPrefs.edit().putString("twitter_users_name_" + sharedPrefs.getInt("current_account", 1), nameS).apply();
                    }
                } else {
                    ok = false;
                    Toast.makeText(context, getResources().getString(R.string.name_char_length), Toast.LENGTH_SHORT).show();
                }

                if(url.getText().length() <= 100 && ok) {
                    if (url.getText().length() > 0){
                        urlS = url.getText().toString();
                    }
                } else {
                    ok = false;
                    Toast.makeText(context, getResources().getString(R.string.url_char_length), Toast.LENGTH_SHORT).show();
                }

                if(location.getText().length() <= 30 && ok) {
                    if (location.getText().length() > 0){
                        locationS = location.getText().toString();
                    }
                } else {
                    ok = false;
                    Toast.makeText(context, getResources().getString(R.string.location_char_length), Toast.LENGTH_SHORT).show();
                }

                if(description.getText().length() <= 160 && ok) {
                    if (description.getText().length() > 0){
                        descriptionS = description.getText().toString();
                    }
                } else {
                    ok = false;
                    Toast.makeText(context, getResources().getString(R.string.description_char_length), Toast.LENGTH_SHORT).show();
                }

                if (ok) {
                    new UpdateInfo(nameS, urlS, locationS, descriptionS).execute();
                    dialog.dismiss();
                }
            }
        });

        dialog.show();
    }

    class UpdateInfo extends AsyncTask<String, Void, Boolean> {

        String name;
        String url;
        String location;
        String description;

        public UpdateInfo(String name, String url, String location, String description) {
            this.name = name;
            this.url = url;
            this.location = location;
            this.description = description;
        }

        protected Boolean doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);

                twitter.updateProfile(name, url, location, description);

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        protected void onPostExecute(Boolean added) {
            if (added) {
                Toast.makeText(context, getResources().getString(R.string.updated_profile), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean bannerUpdate = false;

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

        switch(requestCode) {
            case UCrop.REQUEST_CROP:
                if(resultCode == RESULT_OK) {
                    try {
                        Uri selectedImage = UCrop.getOutput(imageReturnedIntent);

                        if (bannerUpdate) {
                            new UpdateBanner(selectedImage).execute();
                        } else {
                            new UpdateProPic(selectedImage).execute();
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
                    }
                } else if (resultCode == UCrop.RESULT_ERROR) {
                    final Throwable cropError = UCrop.getError(imageReturnedIntent);
                    cropError.printStackTrace();
                }
                break;
            case SELECT_PRO_PIC:
                if(resultCode == RESULT_OK){
                    Uri selectedImage = imageReturnedIntent.getData();
                    bannerUpdate = false;
                    startUcrop(selectedImage);
                }
                break;
            case SELECT_BANNER:
                if(resultCode == RESULT_OK){
                    Uri selectedImage = imageReturnedIntent.getData();
                    bannerUpdate = true;
                    startUcrop(selectedImage);
                }
        }
    }

    public Bitmap decodeSampledBitmapFromResourceMemOpt(
            InputStream inputStream, int reqWidth, int reqHeight) {

        byte[] byteArr = new byte[0];
        byte[] buffer = new byte[1024];
        int len;
        int count = 0;

        try {
            while ((len = inputStream.read(buffer)) > -1) {
                if (len != 0) {
                    if (count + len > byteArr.length) {
                        byte[] newbuf = new byte[(count + len) * 2];
                        System.arraycopy(byteArr, 0, newbuf, 0, count);
                        byteArr = newbuf;
                    }

                    System.arraycopy(buffer, 0, byteArr, count, len);
                    count += len;
                }
            }

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(byteArr, 0, count, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth,
                    reqHeight);
            options.inPurgeable = true;
            options.inInputShareable = true;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            return BitmapFactory.decodeByteArray(byteArr, 0, count, options);

        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options opt, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = opt.outHeight;
        final int width = opt.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private Bitmap getBitmapToSend(Uri uri) throws FileNotFoundException, IOException {
        InputStream input = getContentResolver().openInputStream(uri);

        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();
        onlyBoundsOptions.inJustDecodeBounds = true;
        onlyBoundsOptions.inDither=true;//optional
        onlyBoundsOptions.inPreferredConfig=Bitmap.Config.ARGB_8888;//optional
        BitmapFactory.decodeStream(input, null, onlyBoundsOptions);
        input.close();
        if ((onlyBoundsOptions.outWidth == -1) || (onlyBoundsOptions.outHeight == -1))
            return null;

        int originalSize = (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) ? onlyBoundsOptions.outHeight : onlyBoundsOptions.outWidth;

        double ratio = (originalSize > 500) ? (originalSize / 500) : 1.0;

        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = getPowerOfTwoForSampleRatio(ratio);
        bitmapOptions.inDither=true;//optional
        bitmapOptions.inPreferredConfig=Bitmap.Config.ARGB_8888;
        input = this.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);

        ExifInterface exif = new ExifInterface(IOUtils.getPath(uri, context));
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

        input.close();

        return bitmap;
    }

    private static int getPowerOfTwoForSampleRatio(double ratio){
        int k = Integer.highestOneBit((int)Math.floor(ratio));
        if(k==0) return 1;
        else return k;
    }

    class UpdateBanner extends AsyncTask<String, Void, Boolean> {

        ProgressDialog pDialog;
        private Uri image = null;
        private InputStream stream = null;

        public UpdateBanner(Uri image) {
            this.image = image;
        }

        public UpdateBanner(InputStream stream) {
            this.stream = stream;
        }

        protected void onPreExecute() {
            pDialog = new ProgressDialog(context);
            pDialog.setMessage(getResources().getString(R.string.updating_banner_pic) + "...");
            pDialog.setIndeterminate(true);
            pDialog.setCancelable(false);
            pDialog.show();

        }

        protected Boolean doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);

                if (stream == null) {
                    //create a file to write bitmap data
                    File outputDir = context.getCacheDir(); // context being the Activity pointer
                    File f = File.createTempFile("compose", "picture", outputDir);

                    Bitmap bitmap = getBitmapToSend(image);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                    byte[] bitmapdata = bos.toByteArray();

                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(bitmapdata);
                    fos.flush();
                    fos.close();

                    twitter.updateProfileBanner(f);
                } else {
                    twitter.updateProfileBanner(stream);
                }

                String profileURL = thisUser.getProfileBannerURL();
                sharedPrefs.edit().putString("twitter_background_url_" + sharedPrefs.getInt("current_account", 1), profileURL).apply();

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        protected void onPostExecute(Boolean uploaded) {

            try {
                stream.close();
            } catch (Exception e) {

            }

            try {
                pDialog.dismiss();
            } catch (Exception e) {

            }

            if (uploaded) {
                Toast.makeText(context, getResources().getString(R.string.uploaded), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startUcrop(Uri sourceUri) {
        try {
            UCrop.Options options = new UCrop.Options();
            options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
            options.setCompressionQuality(90);

            File destination = File.createTempFile("ucrop", "jpg", getCacheDir());
            UCrop.of(sourceUri, Uri.fromFile(destination))
                    .withOptions(options)
                    .start(ProfilePager.this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class UpdateProPic extends AsyncTask<String, Void, Boolean> {

        ProgressDialog pDialog;
        private InputStream stream = null;
        private Uri image;

        public UpdateProPic(InputStream stream) {
            this.stream = stream;
        }

        public UpdateProPic(Uri image) {
            this.image = image;
        }

        protected void onPreExecute() {

            pDialog = new ProgressDialog(context);
            pDialog.setMessage(getResources().getString(R.string.updating_pro_pic) + "...");
            pDialog.setIndeterminate(true);
            pDialog.setCancelable(false);
            pDialog.show();

        }

        protected Boolean doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);

                User user;

                if (stream != null) {
                    user = twitter.updateProfileImage(stream);
                } else {
                    //create a file to write bitmap data
                    File outputDir = context.getCacheDir(); // context being the Activity pointer
                    File f = File.createTempFile("compose", "picture", outputDir);

                    Bitmap bitmap = getBitmapToSend(image);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                    byte[] bitmapdata = bos.toByteArray();

                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(bitmapdata);
                    fos.flush();
                    fos.close();

                    user = twitter.updateProfileImage(f);
                }


                String profileURL = user.getOriginalProfileImageURL();
                sharedPrefs.edit().putString("profile_pic_url_" + sharedPrefs.getInt("current_account", 1), profileURL).apply();

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        protected void onPostExecute(Boolean uploaded) {

            try {
                stream.close();
            } catch (Exception e) {

            }

            try {
                pDialog.dismiss();
            } catch (Exception e) {

            }

            if (uploaded) {
                Toast.makeText(context, getResources().getString(R.string.uploaded), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    class GetLists extends AsyncTask<String, Void, ResponseList<UserList>> {

        ProgressDialog pDialog;

        protected void onPreExecute() {

            pDialog = new ProgressDialog(context);
            pDialog.setMessage(getResources().getString(R.string.finding_lists));
            pDialog.setIndeterminate(true);
            pDialog.setCancelable(false);
            pDialog.show();

        }

        protected ResponseList<UserList> doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);

                ResponseList<UserList> lists = twitter.getUserLists(settings.myScreenName);

                return lists;
            } catch (Exception e) {
                return null;
            }
        }

        protected void onPostExecute(final ResponseList<UserList> lists) {

            if (lists != null) {
                Collections.sort(lists, new Comparator<UserList>() {
                    public int compare(UserList result1, UserList result2) {
                        return result1.getName().compareTo(result2.getName());
                    }
                });

                ArrayList<String> names = new ArrayList<String>();
                for(UserList l : lists) {
                    names.add(l.getName());
                }

                try {
                    pDialog.dismiss();

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setItems(names.toArray(new CharSequence[lists.size()]), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            new AddToList(lists.get(i).getId(), thisUser.getId()).execute();
                        }
                    });
                    builder.setTitle(getResources().getString(R.string.choose_list) + ":");
                    builder.create();
                    builder.show();

                } catch (Exception e) {
                    // closed the window
                }


            } else {
                try {
                    pDialog.dismiss();
                } catch (Exception e) {

                }
                Toast.makeText(context, context.getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    class AddToList extends AsyncTask<String, Void, Boolean> {

        long listId;
        long userId;

        public AddToList(long listId, long userId) {
            this.listId = listId;
            this.userId = userId;
        }

        protected Boolean doInBackground(String... urls) {
            try {
                Twitter twitter =  Utils.getTwitter(context, settings);

                twitter.createUserListMember(listId, userId);

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        protected void onPostExecute(Boolean added) {
            if (added) {
                Toast.makeText(context, getResources().getString(R.string.added_to_list), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, getResources().getString(R.string.error), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void expandUrl(final String web) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // resolve the link
                HttpURLConnection connection;
                try {
                    URL address = new URL(web);
                    connection = (HttpURLConnection) address.openConnection(Proxy.NO_PROXY);
                    connection.setConnectTimeout(1000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setReadTimeout(1000);
                    connection.connect();
                    final String expandedURL = connection.getHeaderField("Location");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (expandedURL != null) {
                                website.setText(expandedURL);
                            } else {
                                website.setText(web);
                            }
                            TextUtils.linkifyText(context, website, null, true, "", false);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            website.setText(web);
                            TextUtils.linkifyText(context, website, null, true, "", false);
                        }
                    });
                }

            }
        }).start();
    }

    private void glide(String url, ImageView target) {
        try {
            Glide.with(this).load(url).into(target);
        } catch (Exception e) {
            // activity destroyed
        }
    }
}
