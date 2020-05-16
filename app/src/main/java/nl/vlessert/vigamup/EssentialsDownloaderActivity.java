package nl.vlessert.vigamup;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.File;

public class EssentialsDownloaderActivity extends AppCompatActivity implements View.OnClickListener {

        private DownloadManager downloadManager;
        IntentFilter filter;
        private BroadcastReceiver receiver;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                setContentView(R.layout.essentials_download_menu);

                TextView myButton1 = findViewById(R.id.SCC);
                myButton1.setText(Html.fromHtml("<b>Konami SCC collection</b><br/>MSX<br/><small>Some of Konami's finest with the amazing SCC chip. This package is nicely organised, only the music will be played, no sfx and correct titles and songlengths are included. " +
                        "The games: Contra, F1 Spirit, Kingsvalley 2, Nemesis 2, Nemesis 3, Parodius, Quarth, Salamander, SD Snatcher, Snatcher, Solid Snake, Space Manbow. " +
                        "When zipped all this music is only 800 KB, including the logos! Those were the times...</small>"));

                TextView myButton2 = findViewById(R.id.SNES_1);
                myButton2.setText(Html.fromHtml("<b>Nice SNES Music</b><br/>Snes<br/><small>The SNES has lots of great music... these are a few nice examples. ALPHA, not available online..."));
                        //"//Music by the famous Tim Follin with his brother Mike for programming the tools. "));
                        //"Great on more then one technical level imho, it has progressive rock influences but also the quality of the samples is astonishing, amazing what they squeezed out of the SNES hardware.</small>"));

                TextView myButton3 = findViewById(R.id.TRACKERS_1);
                myButton3.setText(Html.fromHtml("<b>Tracker music archive</b><br/>XM, MOD, S3M, IT, etc.<br/><small>Some really nice scene music... 290+ tracks, only 20MB extracted..."));

                myButton1.setOnClickListener(this);
                myButton2.setOnClickListener(this);
                myButton3.setOnClickListener(this);

                filter = new IntentFilter();
                filter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);

                receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                                Log.d("vigamup", "event!!");
                                if (intent.getAction().equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                                        Log.d("Vigamup", "Download event!!: " + intent.getAction());
                                        Bundle extras = intent.getExtras();
                                        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                                        DownloadManager.Query q = new DownloadManager.Query();
                                        q.setFilterById(extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID));
                                        Cursor c = manager.query(q);
                                        if (c.moveToFirst()) {
                                                int statusColumn = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                                                int downloadStatus = c.getInt(statusColumn);
                                                if (DownloadManager.STATUS_PAUSED == downloadStatus) {
                                                        int reasonColumn =   c.getColumnIndex(DownloadManager.COLUMN_REASON);
                                                        int reasonCode = c.getInt(reasonColumn);
                                                        Log.e("Vigamup", "Download paused: " + reasonCode);
                                                } else if (DownloadManager.STATUS_SUCCESSFUL == downloadStatus
                                                        || DownloadManager.STATUS_FAILED == downloadStatus) {
                                                        Log.i("Vigamup", "Download Ended");
                                                }
                                                String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                                                String name;
                                                if (downloadFileLocalUri != null) {
                                                        File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
                                                        name = mFile.getAbsolutePath();
                                                        sendResult(name);
                                                }
                                        }
                                }
                        }
                };

                registerReceiver(receiver, filter);

        }

        private void sendResult(String name) {
                Intent intent2send = new Intent();
                intent2send.putExtra("DownloadedFile",name);
                setResult(1338,intent2send);
                try {
                        this.unregisterReceiver(receiver);
                } catch (IllegalArgumentException e) { Log.d("vigamup", "unregister receiver failed"); }
                finish();
        }

        @Override
        public void onClick(View view) {
                Uri download_Uri2 = null;
                DownloadManager.Request request2 = null;
                String path = "/ViGaMuP/";
                switch (view.getId()) {
                        case R.id.SCC:
                                download_Uri2 = Uri.parse("http://www.vlessert.nl/vigamup/vigamup_kss_Konami-SCC-Collection.vigamup");
                                path = path.concat("KSS/vigamup_kss_Konami-SCC-Collection.vigamup");
                                break;
                        case R.id.SNES_1:
                                //download_Uri2 = Uri.parse("http://snesmusic.org/v2/download.php?spcNow=plok");
                                //path = path.concat("SPC/plok.rsn");
                                download_Uri2 = Uri.parse("http://192.168.1.21/snes.zip");
                                path = path.concat("tmp/snes.zip");
                                break;
                        case R.id.TRACKERS_1:
                                download_Uri2 = Uri.parse("http://vlessert.nl/vigamup/tracker_music_01.zip");
                                path = path.concat("tmp/tracker_music_01.zip");
                                break;
                }
                request2 = new DownloadManager.Request(download_Uri2);
                request2.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request2.setAllowedOverRoaming(true);
                request2.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, path);
                downloadManager.enqueue(request2);
                Log.d("vigamup", "Enqueud the download... if Android will accept the URL...");
        }
}
