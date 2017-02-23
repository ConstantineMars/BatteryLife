package cmars.batterylife;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    public static final int NO_DATA = -1;

    @BindView(R.id.measurements_textView)
    TextView measurementsTextView;

    @BindView(R.id.statistics_textView)
    TextView statisticsTextView;

    private int lastBatteryLevel = 0;
    private int lastIsChargingStatus = BatteryManager.BATTERY_STATUS_FULL;
    private Boolean lastVote;
    private int totalHappy = NO_DATA;
    private int totalDisappointed = NO_DATA;
    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent batteryStatus) {

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
        }
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Timber.plant(new Timber.DebugTree());
        ButterKnife.bind(this);

        displayStatistics();
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        this.registerReceiver(this.batteryInfoReceiver, intentFilter);
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

    private void displayStatistics() {
        String status = getString(R.string.no_statistics_available);
        if (totalHappy != NO_DATA && totalDisappointed != NO_DATA) {
            status = String.format("total: %d happy, %d disappointed", totalHappy, totalDisappointed);
        }
        if (lastVote != null) {
            status = String.format("%s. you are: %s", status, lastVote ? "happy" : "disappointed");
        }
        statisticsTextView.setText(status);
    }
}
