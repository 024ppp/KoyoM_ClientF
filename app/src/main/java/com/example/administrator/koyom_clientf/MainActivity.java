package com.example.administrator.koyom_clientf;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    Toolbar toolbar;
    ViewPager viewPager;
    TextView show;
    Button btnClear, btnUpd;
    Handler handler;
    // サーバと通信するスレッド
    ClientThread clientThread;
    //インスタンス化無しで使える
    ProcessCommand pc;
    //粉砕機情報表示
    ArrayList<TextView> textViews = new ArrayList<TextView>();

    private static final int SETTING = 8888;
    //運転状況コード
    private static final String JOKYO_WAIT = "0";
    private static final String JOKYO_END = "9";

    //バイブ
    Vibrator vib;
    private long m_vibPattern_read[] = {0, 200};
    private long m_vibPattern_error[] = {0, 200, 200, 500};

    //タイマーの間隔
    private long mDelay = 1000;
    //サーバー接続状況
    private boolean mConnectionStatus = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //バイブ
        vib = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        // view取得
        setViews();
        setTextViews();
        //ハンドラー
        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                // サブスレッドからのメッセージ
                if (msg.what == 0x123) {
                    // 表示する
                    String sMsg = msg.obj.toString();
                    //show.append("\n PCから受信-" + sMsg);
                    selectMotionWhenReceiving(sMsg);
                }
            }
        };
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!mConnectionStatus) {
                    show.setText("サーバー通信なし");
                }
                //粉砕機状況をリクエストする
                sendMsgToServer(pc.FNJ.getString());
                handler.postDelayed(this, mDelay);

                //サーバー接続状況をfalseに
                mConnectionStatus = false;
            }
        }, mDelay);
        //handler.removeCallbacksAndMessages(null);

        //接続先を取得
        SharedPreferences prefs = getSharedPreferences("ConnectionData", Context.MODE_PRIVATE);
        final String ip = prefs.getString("ip", "");
        final int myPort = prefs.getInt("myPort", 0);
        clientThread = new ClientThread(handler, ip, myPort);
        // サーバ接続スレッド開始
        new Thread(clientThread).start();

        //初期メッセージ
        show.setText("サーバー通信がありません。");

    }

    //受信した文字列のコマンド値によって分岐（switch文ではenum使えず...if文汚し）
    private void selectMotionWhenReceiving(String sMsg) {
        String cmd = pc.COMMAND_LENGTH.getCmdText(sMsg);
        String excmd  = pc.COMMAND_LENGTH.getExcludeCmdText(sMsg);

        if (cmd.equals(pc.FNJ.getString())) {
            //受信値を分解、各項目にセット
            String[] info = excmd.split(",");
            //粉砕機状況を表示
            setFunsaiJokyo(info);
            //サーバー接続状況をtrueに
            mConnectionStatus = true;
        }
        else if (cmd.equals(pc.MSG.getString())) {
            show.setText(excmd);
        }
        else if (cmd.equals(pc.ERR.getString())) {
            //バイブ エラー
            vib.vibrate(m_vibPattern_error, -1);
            show.setText(excmd);
        }
    }

    //サーバから受信した粉砕機状況を画面に出す
    private void setFunsaiJokyo(String[] info) {
        TextView textView;

        for (int i = 0; i < textViews.size(); i++) {
            textView = textViews.get(i);
            //受信文字順が、偶数＝処理粉名、奇数＝運転状況
            if (i % 2 == 0) {
                textView.setText(info[i]);
            }
            else {
                setJokyoMsg(info[i], textView);
            }
        }
    }

    //運転状況コードをメッセージに変換してセットする
    private void setJokyoMsg(String code, TextView textView) {
        String msg;

        switch (code) {
            case JOKYO_WAIT:
                msg = "待機";
                textView.setText(msg);
                textView.setTextColor(ContextCompat.getColor(this, R.color.orange));
                break;
            case JOKYO_END:
                msg = "終了";
                textView.setText(msg);
                textView.setTextColor(Color.BLUE);
                break;
            default:
                msg = "工程" + code;
                textView.setText(msg);
                textView.setTextColor(ContextCompat.getColor(this, R.color.green));
                break;
        }
    }

    private void initPage() {
        //1ページ目に戻る
        viewPager.setCurrentItem(0);
        //登録ボタンを無効化
        btnUpd.setEnabled(false);
    }

    @Override
    //クリック処理の実装
    public void onClick(View v) {
        if (v != null) {
            switch (v.getId()) {
                case R.id.btnClear :
                    //Dialog(OK,Cancel Ver.)
                    new AlertDialog.Builder(this)
                            .setTitle("確認")
                            .setMessage("クリアしてよろしいですか？")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // OK button pressed
                                    initPage();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    break;
            }
        }
    }

    @Override
    //タグを読み込んだ時に実行される
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    //サーバへメッセージを送信する
    private void sendMsgToServer(String sMsg) {
        try {
            // メッセージ送信
            Message msg = new Message();
            msg.what = 0x345;   //？
            msg.obj = sMsg;
            clientThread.revHandler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bundle bundle = data.getExtras();

        switch (requestCode) {
            case SETTING:
                Toast.makeText(this, "設定が完了しました。", Toast.LENGTH_SHORT).show();
                break;

            default:
                break;
        }
    }

    private void setViews() {
        toolbar = (Toolbar) findViewById(R.id.toolBar);
        toolbar.setTitle("粉砕機状況");
        setSupportActionBar(toolbar);

        show = (TextView) findViewById(R.id.show);
        btnClear = (Button) findViewById(R.id.btnClear);
        btnUpd = (Button) findViewById(R.id.btnUpd);
        //クリックイベント
        btnClear.setOnClickListener(this);
        btnUpd.setOnClickListener(this);
        //カメラ起動、登録ボタンを無効化
        btnUpd.setEnabled(false);
    }

    //粉砕機状況表示部のsetView
    private void setTextViews(){
        int[] lblId = null;

        lblId = new int[] {R.id.syoriName8, R.id.jokyo8
                           ,R.id.syoriName9, R.id.jokyo9
                           ,R.id.syoriName10, R.id.jokyo10
                           ,R.id.syoriName11, R.id.jokyo11
                           ,R.id.syoriName12, R.id.jokyo12
                           ,R.id.syoriName13, R.id.jokyo13
                           ,R.id.syoriName14, R.id.jokyo14
                           ,R.id.syoriName15, R.id.jokyo15
                           ,R.id.syoriName16, R.id.jokyo16
                           ,R.id.syoriName17, R.id.jokyo17};

        //TextView
        for (int id : lblId) {
            TextView textView = (TextView) findViewById(id);
            //幅崩れ対策
            textView.setWidth(textView.getWidth());
            textViews.add(textView);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PendingIntent pendingIntent = this.createPendingIntent();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            clientThread.finalize();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            //Dialog(OK,Cancel Ver.)
            new AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("終了してもよろしいですか？")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // OK button pressed
                            finishAndRemoveTask();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        return super.onKeyDown(keyCode, event);
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
            //設定画面呼び出し
            Intent intent = new Intent(this, Setting.class);
            int requestCode = SETTING;
            startActivityForResult(intent, requestCode);
            return true;
        }
        else if (id == R.id.action_finish) {
            //Dialog(OK,Cancel Ver.)
            new AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("終了してもよろしいですか？")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // OK button pressed
                            finishAndRemoveTask();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        return super.onOptionsItemSelected(item);
    }

    private PendingIntent createPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent, 0);
    }
}
