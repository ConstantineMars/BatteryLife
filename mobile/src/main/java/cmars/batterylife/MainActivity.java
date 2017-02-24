package cmars.batterylife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class MainActivity extends FragmentActivity {

    public static final int NO_DATA = -1;
    private static final int RC_SIGN_IN = 1001;
    @BindView(R.id.measurements_textView)
    TextView measurementsTextView;
    @BindView(R.id.statistics_textView)
    TextView statisticsTextView;
    @BindView(R.id.sign_in_textVew)
    TextView signInTextView;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private DatabaseReference mDatabase;
    private int lastBatteryLevel = 0;
    private int lastIsChargingStatus = BatteryManager.BATTERY_STATUS_FULL;
    private Boolean lastVote;
    private int mTotalHappy = NO_DATA;
    private int mTotalDisappointed = NO_DATA;
    private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent batteryStatus) {

            readAndDisplayBatteryInfo(batteryStatus);
        }
    };
    private IntentFilter mIntentFilter;
    private GoogleApiClient mGoogleApiClient;

    {
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        mIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        mIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
    }

    @OnClick(R.id.button_up_vote)
    void upVote() {
        lastVote = true;
        displayStatistics();
    }

    @OnClick(R.id.button_down_vote)
    void downVote() {
        lastVote = false;
        displayStatistics();
    }

    @OnClick(R.id.sign_in_button)
    void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Timber.plant(new Timber.DebugTree());
        ButterKnife.bind(this);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
// options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Timber.e("Connection failed");
                    }
                } /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Timber.d("onAuthStateChanged:signed_in:" + user.getUid());
                } else {
                    // User is signed out
                    Timber.d("onAuthStateChanged:signed_out");
                }
                // ...
            }
        };

        mDatabase = FirebaseDatabase.getInstance().getReference();

        displayStatistics();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop() {
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();

        getBatteryStickyInfo();

        this.registerReceiver(this.mBatteryInfoReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void displayMeasurements() {
        String chargingState = "";
        if (lastIsChargingStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            chargingState = ", charging";
        } else if (lastIsChargingStatus == BatteryManager.BATTERY_STATUS_FULL) {
            chargingState = ", full";
        }

        String status = String.format("%d%%%s", lastBatteryLevel, chargingState);
        measurementsTextView.setText(status);
    }

    private void getBatteryStickyInfo() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        readAndDisplayBatteryInfo(batteryStatus);
    }

    private void readAndDisplayBatteryInfo(Intent batteryStatus) {
        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, NO_DATA);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        lastIsChargingStatus = status;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, NO_DATA);
        if (level != lastBatteryLevel && level != NO_DATA) {
            lastBatteryLevel = level;

        }

        displayMeasurements();
        save();
    }

    private void displayStatistics() {
        String status = getString(R.string.no_statistics_available);
        if (mTotalHappy != NO_DATA && mTotalDisappointed != NO_DATA) {
            status = String.format("total: %d happy, %d disappointed", mTotalHappy, mTotalDisappointed);
        }
        if (lastVote != null) {
            status = String.format("%s. you are: %s", status, lastVote ? "happy" : "disappointed");
        }
        statisticsTextView.setText(status);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                // ...
            }
        }
    }

    private void handleSignInResult(GoogleSignInResult result) {
        Timber.d("handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            GoogleSignInAccount acct = result.getSignInAccount();
            signInTextView.setText(acct.getDisplayName());
//            updateUI(true);
        } else {
            // Signed out, show unauthenticated UI.
//            updateUI(false);
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Timber.d("firebaseAuthWithGoogle:" + acct.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Timber.d("signInWithCredential:onComplete:" + task.isSuccessful());

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Timber.w("signInWithCredential", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                        // ...
                    }
                });
    }

    private void save() {
        final BatteryStatus batteryStatus = new BatteryStatus(lastBatteryLevel, lastIsChargingStatus);

        mDatabase.child("battery_status").child("current_status").setValue(batteryStatus);
        mDatabase.child("voting").setValue(lastVote);

        mDatabase.child("battery_status").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                BatteryStatus batteryStatus1 = dataSnapshot.child("current_status").getValue(BatteryStatus.class);
                lastBatteryLevel = batteryStatus1.level;
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }
}
