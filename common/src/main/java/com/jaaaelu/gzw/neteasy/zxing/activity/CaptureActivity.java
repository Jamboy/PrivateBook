package com.jaaaelu.gzw.neteasy.zxing.activity;

import android.animation.LayoutTransition;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.jaaaelu.gzw.neteasy.common.R;
import com.jaaaelu.gzw.neteasy.common.widget.ConfirmDialogFragment;
import com.jaaaelu.gzw.neteasy.model.Book;
import com.jaaaelu.gzw.neteasy.net.BookRequest;
import com.jaaaelu.gzw.neteasy.net.OnBookResultListener;
import com.jaaaelu.gzw.neteasy.util.BookManager;
import com.jaaaelu.gzw.neteasy.zxing.camera.CameraManager;
import com.jaaaelu.gzw.neteasy.zxing.decoding.CaptureActivityHandler;
import com.jaaaelu.gzw.neteasy.zxing.decoding.InactivityTimer;
import com.jaaaelu.gzw.neteasy.zxing.decoding.RGBLuminanceSource;
import com.jaaaelu.gzw.neteasy.zxing.view.ViewfinderView;
import com.raizlabs.android.dbflow.sql.language.CursorResult;
import com.raizlabs.android.dbflow.structure.database.transaction.QueryTransaction;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import static com.jaaaelu.gzw.neteasy.common.tools.UiTool.getLayoutTransition;


public class CaptureActivity extends AppCompatActivity implements Callback, OnBookResultListener<Book> {
    private static final int REQUEST_CODE_SCAN_GALLERY = 100;
    private ImageView mBookImage;
    private TextView mBookName;
    private TextView mBookAuthor;
    private Button mCollectBookBtn;
    private RelativeLayout mBookInfoView;
    private RelativeLayout mRootView;
    private TextView mNotFoundBookHint;
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private ImageButton mFlashSwitchBtn;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private static final float BEEP_VOLUME = 0.10f;
    private boolean vibrate;
    private ProgressDialog mProgress;
    private String photo_path;
    private Bitmap scanBitmap;
    private boolean mIsOpenFlush;
    private boolean mIsScanBook;
    private String mLastIsbn;
    private Book mCurrBook;

    /**
     * 跳转到当前 Activity
     *
     * @param context
     */
    public static void show(Context context) {
        context.startActivity(new Intent(context, CaptureActivity.class));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        CameraManager.init(getApplication());
        initView();
        initListener();
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
    }

    private void initView() {
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_content);
        mFlashSwitchBtn = (ImageButton) findViewById(R.id.ibtn_flash_switch);
        mBookImage = (ImageView) findViewById(R.id.iv_book_image);
        mBookName = (TextView) findViewById(R.id.tv_book_name);
        mBookAuthor = (TextView) findViewById(R.id.tv_book_author);
        mCollectBookBtn = (Button) findViewById(R.id.btn_collect_book);
        mBookInfoView = (RelativeLayout) findViewById(R.id.rl_book_info);
        mRootView = (RelativeLayout) findViewById(R.id.rl_scan_root);
        mNotFoundBookHint = (TextView) findViewById(R.id.tv_not_found_book_hint);

        mRootView.setLayoutTransition(getLayoutTransition());
    }

    private void initListener() {
        findViewById(R.id.ibtn_close_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsScanBook) {
                    finish();
                } else {
                    showDialog();
                }
            }
        });
        mFlashSwitchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mIsOpenFlush) {
                    CameraManager.get().closeFlush();
                    mFlashSwitchBtn.setImageResource(R.drawable.ic_flash_on);
                } else {
                    CameraManager.get().openFlush();
                    mFlashSwitchBtn.setImageResource(R.drawable.ic_flash_off);
                }
                mIsOpenFlush = !mIsOpenFlush;
            }
        });
        mCollectBookBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                collectBookInLocal();
            }
        });
    }

    private void collectBookInLocal() {
        BookManager.saveBook(mCurrBook);

        mBookInfoView.setVisibility(View.GONE);

        mIsScanBook = false;

        mLastIsbn = "";
    }

//
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.scanner_menu, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()){
//            case R.id.scan_local:
//                //打开手机中的相册
//                Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT); //"android.intent.action.GET_CONTENT"
//                innerIntent.setType("image/*");
//                Intent wrapperIntent = Intent.createChooser(innerIntent, "选择二维码图片");
//                this.startActivityForResult(wrapperIntent, REQUEST_CODE_SCAN_GALLERY);
//                return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    protected void onActivityResult(final int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_SCAN_GALLERY:
                    //获取选中图片的路径
                    Cursor cursor = getContentResolver().query(data.getData(), null, null, null, null);
                    if (cursor.moveToFirst()) {
                        photo_path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                    }
                    cursor.close();

                    mProgress = new ProgressDialog(CaptureActivity.this);
                    mProgress.setMessage("正在扫描...");
                    mProgress.setCancelable(false);
                    mProgress.show();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Result result = scanningImage(photo_path);
                            if (result != null) {
//                                Message m = handler.obtainMessage();
//                                m.what = R.id.decode_succeeded;
//                                m.obj = result.getText();
//                                handler.sendMessage(m);
                                Intent resultIntent = new Intent();
                                Bundle bundle = new Bundle();
                                bundle.putString("result", result.getText());
//                                bundle.putParcelable("bitmap",result.get);
                                resultIntent.putExtras(bundle);
                                CaptureActivity.this.setResult(RESULT_OK, resultIntent);

                            } else {
                                Message m = handler.obtainMessage();
                                m.what = R.id.decode_failed;
                                m.obj = "Scan failed!";
                                handler.sendMessage(m);
                            }
                        }
                    }).start();
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 扫描二维码图片的方法
     *
     * @param path
     * @return
     */
    public Result scanningImage(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        Hashtable<DecodeHintType, String> hints = new Hashtable<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF8"); //设置二维码内容的编码

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 先获取原大小
        scanBitmap = BitmapFactory.decodeFile(path, options);
        options.inJustDecodeBounds = false; // 获取新的大小
        int sampleSize = (int) (options.outHeight / (float) 200);
        if (sampleSize <= 0)
            sampleSize = 1;
        options.inSampleSize = sampleSize;
        scanBitmap = BitmapFactory.decodeFile(path, options);
        RGBLuminanceSource source = new RGBLuminanceSource(scanBitmap);
        BinaryBitmap bitmap1 = new BinaryBitmap(new HybridBinarizer(source));
        QRCodeReader reader = new QRCodeReader();
        try {
            return reader.decode(bitmap1, hints);
        } catch (NotFoundException | ChecksumException | FormatException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.scanner_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;

        //quit the scan view
//		cancelScanButton.setOnClickListener(new OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				CaptureActivity.this.finish();
//			}
//		});
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    /**
     * Handler scan result
     *
     * @param result
     * @param barcode
     */
    public void handleDecode(Result result, Bitmap barcode) {
        Log.e("handleDecode", "handleDecode ___ " + result.toString());
        inactivityTimer.onActivity();
        String resultString = result.getText();
        if (resultString.equals(mLastIsbn)) {
            handler.restartPreviewAndDecode();
            return;
        }
        //FIXME
        if (TextUtils.isEmpty(resultString)) {
            Toast.makeText(CaptureActivity.this, "Scan failed!", Toast.LENGTH_SHORT).show();
        } else {
            BookRequest.getInstance().queryBookByISBN(resultString, this);
            mLastIsbn = resultString;
        }
//        CaptureActivity.this.finish();
        handler.restartPreviewAndDecode();
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException | RuntimeException ioe) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(
                    R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    @Override
    public void onSuccess(Book book) {
        playBeepSoundAndVibrate();
        mNotFoundBookHint.setVisibility(View.GONE);
        mBookInfoView.setVisibility(View.VISIBLE);
        setBookInfo(book);
        mIsScanBook = true;
        queryBook(book);
    }

    private void queryBook(final Book book) {
        BookManager.queryBookById(new QueryTransaction.QueryResultCallback<Book>() {
            @Override
            public void onQueryResult(QueryTransaction<Book> transaction, @NonNull CursorResult<Book> tResult) {
                if (tResult.getCount() == 0) {
                    mCollectBookBtn.setBackground(ContextCompat.getDrawable(CaptureActivity.this, R.drawable.btn_with_flat_ripple));
                    mCollectBookBtn.setText("收藏");
                    mCollectBookBtn.setTextColor(ContextCompat.getColor(CaptureActivity.this, R.color.white));
                    mCollectBookBtn.setEnabled(true);
                    return;
                }
                if (book.getTitle().equals(tResult.getItem(0).getTitle())) {
                    mBookInfoView.setVisibility(View.VISIBLE);
                    mCollectBookBtn.setBackground(ContextCompat.getDrawable(CaptureActivity.this, R.drawable.btn_with_already_collect));
                    mCollectBookBtn.setText("已收藏");
                    mCollectBookBtn.setTextColor(ContextCompat.getColor(CaptureActivity.this, R.color.colorPrimary));
                    mCollectBookBtn.setEnabled(false);
                    mIsScanBook = false;
                }
            }
        }, book.getId());
    }

    private void setBookInfo(Book book) {
        Glide.with(this)
                .load(book.getImage())
                .into(mBookImage);
        mBookName.setText(book.getTitle());
        mBookAuthor.setText(book.getAuthor().get(0));

        this.mCurrBook = book;
    }

    @Override
    public void onFailure(Throwable t) {
        mIsScanBook = false;
        playBeepSoundAndVibrate();
        mNotFoundBookHint.setVisibility(View.VISIBLE);
        mBookInfoView.setVisibility(View.GONE);
        if (t != null) {
            t.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        if (!mIsScanBook) {
            super.onBackPressed();
        } else {
            showDialog();
        }
    }

    private void showDialog() {
        ConfirmDialogFragment fragment = ConfirmDialogFragment.newInstance(getString(R.string.has_no_collect_hint));
        fragment.show(getSupportFragmentManager(), "");
    }
}