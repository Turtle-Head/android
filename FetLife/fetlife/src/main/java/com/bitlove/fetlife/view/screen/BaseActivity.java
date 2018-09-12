package com.bitlove.fetlife.view.screen;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bitlove.fetlife.event.ServiceCallFinishedEvent;
import com.bitlove.fetlife.model.service.FetLifeApiIntentService;
import com.bitlove.fetlife.session.UserSessionManager;
import com.bitlove.fetlife.view.screen.resource.ConversationsActivity;
import com.bitlove.fetlife.view.screen.resource.FeedActivity;
import com.bitlove.fetlife.view.screen.resource.TurboLinksViewActivity;
import com.crashlytics.android.Crashlytics;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.internal.BaselineLayout;
import com.google.android.material.navigation.NavigationView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.transition.Transition;

import android.transition.Fade;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bitlove.fetlife.BuildConfig;
import com.bitlove.fetlife.FetLifeApplication;
import com.bitlove.fetlife.R;
import com.bitlove.fetlife.view.screen.component.ActivityComponent;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.facebook.drawee.view.SimpleDraweeView;
import com.mikepenz.iconics.context.IconicsContextWrapper;
import com.mikepenz.iconics.utils.IconicsMenuInflaterUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    public static final int PERMISSION_REQUEST_PICTURE_UPLOAD = 10000;
    public static final int PERMISSION_REQUEST_VIDEO_UPLOAD = 20000;
    public static final int PERMISSION_REQUEST_LOCATION = 30000;

    public static final String EXTRA_NOTIFICATION_SOURCE_TYPE = "EXTRA_NOTIFICATION_SOURCE_TYPE";
    public static final String EXTRA_SELECTED_BOTTOM_NAV_ITEM = "EXTRA_SELECTED_BOTTOM_NAV_ITEM";
    private static final int BOTTOM_BAR_ORDER_MESSAGES = 1;
    private static final int BOTTOM_BAR_ORDER_REQUESTS = 2;
    private static final int BOTTOM_BAR_ORDER_NOTIFS = 3;
    private static final int MAX_NOTIFICATION_COUNT = 99;

    protected boolean waitingForResult;
    protected ProgressBar progressIndicator;
    private View toolBar;
    protected SimpleDraweeView toolBarImage;
    protected TextView toolBarTitle;
    protected boolean finishAfterNavigation;
    protected Intent pendingNavigationIntent;

    List<ActivityComponent> activityComponentList = new ArrayList<>();

    protected void addActivityComponent(ActivityComponent activityComponent) {
        activityComponentList.add(activityComponent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
//        getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
//        getWindow().setSharedElementExitTransition(makeEnterTransition());
////        getWindow().setEnterTransition(null);
////        getWindow().setExitTransition(null);
//        getWindow().setAllowEnterTransitionOverlap(false);
//        getWindow().setAllowReturnTransitionOverlap(false);
        logEvent();

        if (savedInstanceState == null) {
            String notificationSourceType = getIntent().getStringExtra(EXTRA_NOTIFICATION_SOURCE_TYPE);
            if (notificationSourceType != null) {
                getFetLifeApplication().getNotificationParser().clearNotification(notificationSourceType);
            }
        }

        onCreateActivityComponents();
        onSetContentView();

//        getWindow().setEnterTransition(makeExcludeTransition());
//        getWindow().setExitTransition(makeExcludeTransition());

        TextView previewText = (TextView)findViewById(R.id.text_preview);
        if (previewText != null) {
            if (BuildConfig.PREVIEW) {
                RotateAnimation rotate= (RotateAnimation) AnimationUtils.loadAnimation(this,R.anim.preview_rotation);
                previewText.setAnimation(rotate);
                previewText.setVisibility(View.VISIBLE);
            } else {
                previewText.setVisibility(View.GONE);
            }
        }

        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityCreated(this, savedInstanceState);
        }

        toolBar.setVisibility(hasToolbar() ? View.VISIBLE : View.GONE);

        final BottomNavigationView bottomNavigation = findViewById(R.id.navigation_bottom);
        if (hasBottomNavigation() && bottomNavigation != null) {
            bottomNavigation.setVisibility(View.VISIBLE);

            final ActivityOptionsCompat navOptions = ActivityOptionsCompat.
                    makeSceneTransitionAnimation(BaseActivity.this, bottomNavigation, "bottomNavBar");

            Menu menu = bottomNavigation.getMenu();
            IconicsMenuInflaterUtil.inflate(getMenuInflater(), this, R.menu.menu_navigation_bottom, menu);
            final int selectedMenuItem = getIntent().getIntExtra(EXTRA_SELECTED_BOTTOM_NAV_ITEM,-1);
            FetLifeApiIntentService.startApiCall(this,FetLifeApiIntentService.ACTION_APICALL_NOTIFICATION_COUNTS);

            bottomNavigation.setVisibility(View.VISIBLE);
            if (selectedMenuItem >0) {
                bottomNavigation.setSelectedItemId(selectedMenuItem);
            }

            SharedPreferences userPrefs = getFetLifeApplication().getUserSessionManager().getActiveUserPreferences();
            int messageCount = Math.min(MAX_NOTIFICATION_COUNT,userPrefs.getInt(UserSessionManager.PREF_KEY_MESSAGE_COUNT, -1));
            int requestCount = Math.min(MAX_NOTIFICATION_COUNT,userPrefs.getInt(UserSessionManager.PREF_KEY_REQUEST_COUNT, -1));
            int notifCount = Math.min(MAX_NOTIFICATION_COUNT,userPrefs.getInt(UserSessionManager.PREF_KEY_NOTIF_COUNT, -1));

            initNoActivePadding(bottomNavigation);

            initBadgeCount(bottomNavigation,BOTTOM_BAR_ORDER_MESSAGES,messageCount);
            initBadgeCount(bottomNavigation,BOTTOM_BAR_ORDER_REQUESTS,requestCount);
            initBadgeCount(bottomNavigation,BOTTOM_BAR_ORDER_NOTIFS,notifCount);

            final DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);

            final BottomNavigationView.OnNavigationItemSelectedListener navigationItemSelectedListener = new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                    Intent intent;
                    switch (menuItem.getItemId()) {
                        case R.id.navigation_bottom_feed:
                            bottomNavigation.setOnNavigationItemSelectedListener(null);
                            intent = new Intent(BaseActivity.this, FeedActivity.class);
                            intent.putExtra(EXTRA_SELECTED_BOTTOM_NAV_ITEM,menuItem.getItemId());
                            BaseActivity.this.startActivity(intent,navOptions.toBundle());
                            setFinishAfterNavigation(true);
                            //FeedActivity.startActivity(BaseActivity.this, bottomNavigation, "bottomNavBar");
//                                finishAfterTransition();
                            break;
                        case R.id.navigation_bottom_inbox:
                            bottomNavigation.setOnNavigationItemSelectedListener(null);
                            intent = new Intent(BaseActivity.this, ConversationsActivity.class);
                            intent.putExtra(EXTRA_SELECTED_BOTTOM_NAV_ITEM,menuItem.getItemId());
                            BaseActivity.this.startActivity(intent,navOptions.toBundle());
//                                finishAfterTransition();
//                            ConversationsActivity.startActivity(BaseActivity.this, null, false, bottomNavigation, "bottomNavBar");
                            setFinishAfterNavigation(true);
                            break;
                        case R.id.navigation_bottom_requests:
                            bottomNavigation.setOnNavigationItemSelectedListener(null);
                            TurboLinksViewActivity.startActivity(BaseActivity.this,"requests",BaseActivity.this.getString(R.string.title_activity_friendrequests),R.id.navigation_bottom_requests,navOptions.toBundle());
//                                finishAfterTransition();
                            setFinishAfterNavigation(true);
                            break;
                        case R.id.navigation_bottom_notifications:
                            bottomNavigation.setOnNavigationItemSelectedListener(null);
                            TurboLinksViewActivity.startActivity(BaseActivity.this,"notifications",BaseActivity.this.getString(R.string.title_activity_notifications),R.id.navigation_bottom_notifications,navOptions.toBundle());
//                              finishAfterTransition();
                            setFinishAfterNavigation(true);
                            break;
                        case R.id.navigation_bottom_menu_drawer:
                            if (drawerLayout != null) {
                                if (drawerLayout.isDrawerOpen(Gravity.RIGHT)) {
                                    if (selectedMenuItem > 0) {
                                        bottomNavigation.setOnNavigationItemSelectedListener(null);
                                        bottomNavigation.setSelectedItemId(R.id.navigation_bottom_menu_drawer);
                                        bottomNavigation.setOnNavigationItemSelectedListener(this);
                                    }
                                    drawerLayout.closeDrawer(Gravity.RIGHT);
                                } else {
                                    bottomNavigation.setOnNavigationItemSelectedListener(null);
                                    bottomNavigation.setSelectedItemId(R.id.navigation_bottom_menu_drawer);
                                    bottomNavigation.setOnNavigationItemSelectedListener(this);
                                    drawerLayout.openDrawer(Gravity.RIGHT);
                                }
                            }
                        return true;
                    }
                    return false;
                }
            };
            bottomNavigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
            drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
                @Override
                public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {

                }

                @Override
                public void onDrawerOpened(@NonNull View drawerView) {
                    bottomNavigation.setOnNavigationItemSelectedListener(null);
                    bottomNavigation.setSelectedItemId(R.id.navigation_bottom_menu_drawer);
                    bottomNavigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
                }

                @Override
                public void onDrawerClosed(@NonNull View drawerView) {
                    Intent pendingNavigationIntent = getPendingNavigationIntent();
                    if (pendingNavigationIntent != null) {
                        startActivity(pendingNavigationIntent,navOptions.toBundle());
                    } else if (selectedMenuItem > 0) {
                        bottomNavigation.setOnNavigationItemSelectedListener(null);
                        bottomNavigation.setSelectedItemId(selectedMenuItem);
                        bottomNavigation.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
                    }
                }

                @Override
                public void onDrawerStateChanged(int newState) {

                }
            });
        } else if (bottomNavigation != null){
            bottomNavigation.setVisibility(View.GONE);
        }
    }

    protected boolean hasToolbar() {
        return true;
    }

    protected boolean hasBottomNavigation() {
        return true;
    }

    private void initNoActivePadding(BottomNavigationView bottomNavigation) {
        BottomNavigationMenuView bottomNavigationMenuView =
                (BottomNavigationMenuView) bottomNavigation.getChildAt(0);



        int childCount = bottomNavigationMenuView.getChildCount();

        for (int i = 0; i< childCount; i++) {
            View childView = bottomNavigationMenuView.getChildAt(i);
            if (!(childView instanceof  BottomNavigationItemView)) {
                continue;
            }
            BottomNavigationItemView itemView = (BottomNavigationItemView) childView;
            View activeLabel = itemView.findViewById(com.google.android.material.R.id.largeLabel);
            if (activeLabel != null && activeLabel instanceof TextView) {
                ((TextView)activeLabel).setPadding(0,0,0,0);
            }

        }
    }

    private void initBadgeCount(BottomNavigationView bottomNavigation, int bottomBarOrder, int badgeCount) {

        BottomNavigationMenuView bottomNavigationMenuView =
                (BottomNavigationMenuView) bottomNavigation.getChildAt(0);

        if (bottomNavigationMenuView.getChildCount() <= bottomBarOrder) {
            Crashlytics.logException(new Exception("Invalid bottom bar order number"));
            return;
        }
        View childView = bottomNavigationMenuView.getChildAt(bottomBarOrder);
        if (!(childView instanceof  BottomNavigationItemView)) {
            Crashlytics.logException(new Exception("Invalid bottom bar child view"));
            return;
        }
        BottomNavigationItemView itemView = (BottomNavigationItemView) childView;

        View badge = LayoutInflater.from(this)
                .inflate(R.layout.notification_badge, bottomNavigationMenuView, false);

        if (badgeCount <= 0) {
            badge.setVisibility(View.GONE);
        } else {
            badge.setVisibility(View.VISIBLE);
            TextView badgeCountText = badge.findViewById(R.id.badge_count);
            badgeCountText.setText(Integer.toString(badgeCount));
        }
        itemView.addView(badge);
    }

    public boolean doFinishAfterNavigation() {
        return finishAfterNavigation;
    }

    public void setFinishAfterNavigation(boolean finishAfterNavigation) {
        this.finishAfterNavigation = finishAfterNavigation;
    }

    public Intent getPendingNavigationIntent() {
        return pendingNavigationIntent;
    }

    public void setPendingNavigationIntent(Intent pendingNavigationIntent) {
        this.pendingNavigationIntent = pendingNavigationIntent;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNotificationCountCallFinished(ServiceCallFinishedEvent serviceCallFinishedEvent) {
        if (serviceCallFinishedEvent.getServiceCallAction() == FetLifeApiIntentService.ACTION_APICALL_NOTIFICATION_COUNTS) {
            final BottomNavigationView bottomNavigation = findViewById(R.id.navigation_bottom);
            if (bottomNavigation != null && bottomNavigation.getVisibility() == View.VISIBLE) {
                SharedPreferences userPrefs = getFetLifeApplication().getUserSessionManager().getActiveUserPreferences();
                int messageCount = Math.min(MAX_NOTIFICATION_COUNT,userPrefs.getInt(UserSessionManager.PREF_KEY_MESSAGE_COUNT, -1));
                int requestCount = Math.min(MAX_NOTIFICATION_COUNT,userPrefs.getInt(UserSessionManager.PREF_KEY_REQUEST_COUNT, -1));
                int notifCount = Math.min(MAX_NOTIFICATION_COUNT,userPrefs.getInt(UserSessionManager.PREF_KEY_NOTIF_COUNT, -1));
                setBadgeCount(bottomNavigation,BOTTOM_BAR_ORDER_MESSAGES,messageCount);
                setBadgeCount(bottomNavigation,BOTTOM_BAR_ORDER_REQUESTS,requestCount);
                setBadgeCount(bottomNavigation,BOTTOM_BAR_ORDER_NOTIFS,notifCount);
            }
        }
    }

    private void setBadgeCount(BottomNavigationView bottomNavigation, int bottomBarOrder, int badgeCount) {
        BottomNavigationMenuView bottomNavigationMenuView =
                (BottomNavigationMenuView) bottomNavigation.getChildAt(0);

        if (bottomNavigationMenuView.getChildCount() <= bottomBarOrder) {
            Crashlytics.logException(new Exception("Invalid bottom bar order number"));
            return;
        }
        View childView = bottomNavigationMenuView.getChildAt(bottomBarOrder);
        if (!(childView instanceof  BottomNavigationItemView)) {
            Crashlytics.logException(new Exception("Invalid bottom bar child view"));
            return;
        }

        BottomNavigationItemView itemView = (BottomNavigationItemView) childView;

        View badgeFrameView = itemView.findViewById(R.id.badge_frame);
        if (badgeFrameView == null) {
            return;
        }

        badgeFrameView.setVisibility(badgeCount >0 ? View.VISIBLE : View.GONE);

        TextView badgeCountText = badgeFrameView.findViewById(R.id.badge_count);
        badgeCountText.setText(Integer.toString(badgeCount));
    }

    protected void logEvent() {
        Answers.getInstance().logCustom(
                new CustomEvent(BaseActivity.this.getClass().getSimpleName()));
    }

    protected abstract void onCreateActivityComponents();

    protected abstract void onSetContentView();

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);
        initProgressIndicator();
        toolBar = findViewById(R.id.toolbar);
        toolBarImage = (SimpleDraweeView) findViewById(R.id.toolbar_image);
        toolBarTitle = (TextView) findViewById(R.id.toolbar_title);
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        initProgressIndicator();
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle("");
        if (toolBarTitle != null) {
            toolBarTitle.setText(title);
        }
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle("");
        if (toolBarTitle != null) {
            toolBarTitle.setText(titleId);
        }
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        initProgressIndicator();
    }

    protected void initProgressIndicator() {
        progressIndicator = (ProgressBar) findViewById(R.id.toolbar_progress_indicator);
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityPaused(this);
        }
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityResumed(this);
        }
        waitingForResult = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        getFetLifeApplication().getEventBus().register(this);
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityStarted(this);
        }
    }



    @Override
    protected void onStop() {
        super.onStop();
        getFetLifeApplication().getEventBus().unregister(this);
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityStopped(this);
        }
        if (finishAfterNavigation) {
//            finishAfterTransition();
            finish();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        BaseActivity baseActivity = getFetLifeApplication().getForegroundActivity() instanceof BaseActivity ? (BaseActivity) getFetLifeApplication().getForegroundActivity() : null;
        if (baseActivity != null && baseActivity.getIntent().getIntExtra(EXTRA_SELECTED_BOTTOM_NAV_ITEM,-1) >= 0) {
//            finishAfterTransition();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityDestroyed(this);
        }
        waitingForResult = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivitySaveInstanceState(this, outState);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Boolean result = null;
        for (ActivityComponent activityComponent : activityComponentList) {
            Boolean componentResult = activityComponent.onActivityOptionsItemSelected(this, item);
            if (componentResult == null) {
                continue;
            }
            if (result == null) {
                result = componentResult;
                continue;
            }
            result |= componentResult;
        }
        if (result == null) {
            return super.onOptionsItemSelected(item);
        }
        return result;
    }


    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Boolean result = false;
        for (ActivityComponent activityComponent : activityComponentList) {
            Boolean componentResult = activityComponent.onActivityNavigationItemSelected(this, item);
            if (componentResult == null) {
                continue;
            }
            if (result == null) {
                result = componentResult;
                continue;
            }
            result |= componentResult;
        }
        return result;
    }

    @Override
    public void onBackPressed() {
        Boolean result = null;
        for (ActivityComponent activityComponent : activityComponentList) {
            Boolean componentResult = activityComponent.onActivityBackPressed(this);
            if (componentResult == null) {
                continue;
            }
            if (result == null) {
                result = componentResult;
                continue;
            }
            result |= componentResult;
        }
        if (result == null || !result) {
            final int selectedMenuItem = getIntent().getIntExtra(EXTRA_SELECTED_BOTTOM_NAV_ITEM,-1);
            if (selectedMenuItem >= 0) {
                finish();
            }
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Boolean result = null;
        for (ActivityComponent activityComponent : activityComponentList) {
            Boolean componentResult = activityComponent.onActivityCreateOptionsMenu(this, menu);
            if (componentResult == null) {
                continue;
            }
            if (result == null) {
                result = componentResult;
                continue;
            }
            result |= componentResult;
        }
        if (result == null) {
            return super.onCreateOptionsMenu(menu);
        }
        return result;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        Boolean result = null;
        for (ActivityComponent activityComponent : activityComponentList) {
            Boolean componentResult = activityComponent.onActivityKeyDown(this, keyCode, e);
            if (componentResult == null) {
                continue;
            }
            if (result == null) {
                result = componentResult;
                continue;
            }
            result |= componentResult;
        }
        if (result == null  || !result) {
            return super.onKeyDown(keyCode, e);
        }
        return result;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onActivityResult(this, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for (ActivityComponent activityComponent : activityComponentList) {
            activityComponent.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        super.startActivityForResult(intent, requestCode, options);
        waitingForResult = true;
    }

    public boolean isWaitingForResult() {
        return waitingForResult;
    }

    public void onWaitingForResult() {
        this.waitingForResult = true;
    }

    public void showProgress() {
        progressIndicator.setVisibility(View.VISIBLE);
    }

    public void dismissProgress() {
        progressIndicator.setVisibility(View.INVISIBLE);
    }

    public void showToast(final String text) {
        getFetLifeApplication().showToast(text);
    }

    public FetLifeApplication getFetLifeApplication() {
        return (FetLifeApplication) getApplication();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(IconicsContextWrapper.wrap(newBase));
    }

    public static Transition makeExcludeTransition() {
        Transition fade = new Fade();
        fade.excludeTarget(R.id.app_bar, true);
        fade.excludeTarget(android.R.id.navigationBarBackground, true);
        fade.excludeTarget(android.R.id.statusBarBackground, true);
        return fade;
    }
}
