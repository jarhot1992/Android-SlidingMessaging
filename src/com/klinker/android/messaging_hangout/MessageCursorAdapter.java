package com.klinker.android.messaging_hangout;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.graphics.*;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.SmsManager;
import android.text.Html;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.*;
import com.android.mms.transaction.HttpUtils;
import com.android.mms.util.SendingProgressTokenManager;
import com.google.android.mms.APN;
import com.google.android.mms.APNHelper;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.klinker.android.messaging_donate.R;
import com.klinker.android.messaging_donate.StripAccents;
import com.klinker.android.messaging_donate.receivers.DisconnectWifi;
import com.klinker.android.messaging_sliding.ImageViewer;
import com.klinker.android.messaging_sliding.MainActivity;
import com.klinker.android.messaging_sliding.emojis.*;
import com.klinker.android.messaging_sliding.receivers.NotificationReceiver;

import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageCursorAdapter extends CursorAdapter {

    private final Activity context;
    private final String myId;
    private final String inboxNumbers;
    private final int threadPosition;
    private final String threadIds;
    private final Bitmap contactImage;
    private final Bitmap myImage;
    private SharedPreferences sharedPrefs;
    private ContentResolver contentResolver;
    private Cursor mCursor;
    private Paint paint;
    private Typeface font;
    private final LayoutInflater mInflater;

    public DisconnectWifi discon;
    public WifiInfo currentWifi;
    public boolean currentWifiState;
    public boolean currentDataState;

    public MessageCursorAdapter(Activity context, String myId, String inboxNumbers, String ids, Cursor query, int threadPosition) {
        super(context, query, 0);
        this.context = context;
        this.myId = myId;
        this.inboxNumbers = inboxNumbers;
        this.threadPosition = threadPosition;
        this.threadIds = ids;
        this.sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        this.contentResolver = context.getContentResolver();
        this.mInflater = LayoutInflater.from(context);
        this.mCursor = query;

        Bitmap input;

        try
        {
            input = getFacebookPhoto(inboxNumbers);
        } catch (NumberFormatException e)
        {
            input = null;
        }

        if (input == null)
        {
            if (sharedPrefs.getBoolean("ct_darkContactImage", false))
            {
                input = drawableToBitmap(context.getResources().getDrawable(R.drawable.default_avatar_dark));
            } else
            {
                input = drawableToBitmap(context.getResources().getDrawable(R.drawable.default_avatar));
            }
        }

        contactImage = Bitmap.createScaledBitmap(input, MainActivity.contactWidth, MainActivity.contactWidth, true);

        InputStream input2;

        try
        {
            input2 = openDisplayPhoto(Long.parseLong(this.myId));
        } catch (NumberFormatException e)
        {
            input2 = null;
        }

        if (input2 == null)
        {
            if (sharedPrefs.getBoolean("ct_darkContactImage", false))
            {
                input2 = context.getResources().openRawResource(R.drawable.default_avatar_dark);
            } else
            {
                input2 = context.getResources().openRawResource(R.drawable.default_avatar);
            }
        }

        Bitmap im;

        try
        {
            im = Bitmap.createScaledBitmap(BitmapFactory.decodeStream(input2), MainActivity.contactWidth, MainActivity.contactWidth, true);
        } catch (Exception e)
        {
            if (sharedPrefs.getBoolean("ct_darkContactImage", false))
            {
                im = Bitmap.createScaledBitmap(drawableToBitmap(context.getResources().getDrawable(R.drawable.default_avatar_dark)), MainActivity.contactWidth, MainActivity.contactWidth, true);
            } else
            {
                im = Bitmap.createScaledBitmap(drawableToBitmap(context.getResources().getDrawable(R.drawable.default_avatar)), MainActivity.contactWidth, MainActivity.contactWidth, true);
            }
        }

        myImage = im;

        paint = new Paint();
        float densityMultiplier = context.getResources().getDisplayMetrics().density;
        float scaledPx = Integer.parseInt(sharedPrefs.getString("text_size", "14")) * densityMultiplier;
        paint.setTextSize(scaledPx);
        font = null;

        if (sharedPrefs.getBoolean("custom_font", false))
        {
            font = Typeface.createFromFile(sharedPrefs.getString("custom_font_path", ""));
            paint.setTypeface(font);
        }
    }

    private int getItemViewType(Cursor query) {
        try
        {
            String s = query.getString(query.getColumnIndex("msg_box"));

            if (s != null) {
                if (query.getInt(query.getColumnIndex("msg_box")) == 4)
                {
                    return 1;
                } else if (query.getInt(query.getColumnIndex("msg_box")) == 5)
                {
                    return 1;
                } else if (query.getInt(query.getColumnIndex("msg_box")) == 1)
                {
                    return 0;
                } else if (query.getInt(query.getColumnIndex("msg_box")) == 2)
                {
                    return 1;
                }
            } else
            {
                String type = query.getString(query.getColumnIndex("type"));

                if (type.equals("2") || type.equals("4") || type.equals("5") || type.equals("6"))
                {
                    return 1;
                } else
                {
                    return 0;
                }

            }
        } catch (Exception e)
        {
            return 0;
        }

        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        Cursor cursor = (Cursor) getItem(getCount() - 1 - position);
        return getItemViewType(cursor);
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public void bindView(final View view, Context mContext, final Cursor cursor) {
        final ViewHolder holder = (ViewHolder) view.getTag();
        holder.media.setVisibility(View.GONE);

        boolean sent = false;
        boolean mms = false;
        String image = null;
        String body = "";
        String date = "0";
        String id = "";
        boolean sending = false;
        boolean error = false;
        boolean group = false;
        String sender = "";
        String status = "-1";

        String dateType = "date";

        if (sharedPrefs.getBoolean("show_original_timestamp", false))
        {
            dateType = "date_sent";
        }

        try
        {
            String s = cursor.getString(cursor.getColumnIndex("msg_box"));

            if (s != null) {
                id = cursor.getString(cursor.getColumnIndex("_id"));
                mms = true;
                body = "";

                date = Long.parseLong(cursor.getString(cursor.getColumnIndex("date"))) * 1000 + "";

                String number = getAddressNumber(Integer.parseInt(cursor.getString(cursor.getColumnIndex("_id")))).trim();

                String[] numbers = number.split(" ");

                if (cursor.getInt(cursor.getColumnIndex("msg_box")) == 4)
                {
                    sending = true;
                    sent = true;
                } else if (cursor.getInt(cursor.getColumnIndex("msg_box")) == 5)
                {
                    error = true;
                    sent = true;
                } else if (cursor.getInt(cursor.getColumnIndex("msg_box")) == 1)
                {
                    sent = false;
                } else if (cursor.getInt(cursor.getColumnIndex("msg_box")) == 2)
                {
                    sent = true;
                }

                if (numbers.length > 2)
                {
                    group = true;
                    sender = numbers[0];
                }

                if (cursor.getInt(cursor.getColumnIndex("read")) == 0)
                {
                    final String SmsMessageId = cursor.getString(cursor.getColumnIndex("_id"));

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            ContentValues values = new ContentValues();
                            values.put("read", true);
                            contentResolver.update(Uri.parse("content://mms/inbox"), values, "_id=" + SmsMessageId, null);
                        }
                    }).start();
                }

                final String selectionPart = "mid=" + cursor.getString(cursor.getColumnIndex("_id"));

                if (!group) {
                    holder.media.setVisibility(View.VISIBLE);

                    final String idF = id;

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(250);
                            } catch (Exception e) {

                            }

                            String body = "";
                            String image = null;
                            String video = null;
                            String audio = null;

                            Uri uri = Uri.parse("content://mms/part");
                            Cursor query = contentResolver.query(uri, null, selectionPart, null, null);

                            if (query.moveToFirst()) {
                                do {
                                    String partId = query.getString(query.getColumnIndex("_id"));
                                    String type = query.getString(query.getColumnIndex("ct"));
                                    String body2 = "";

                                    if ("text/plain".equals(type)) {
                                        String data = query.getString(query.getColumnIndex("_data"));
                                        if (data != null) {
                                            body2 = getMmsText(partId, context);
                                            body += body2;
                                        } else {
                                            body2 = query.getString(query.getColumnIndex("text"));
                                            body += body2;
                                        }
                                    }

                                    if ("image/jpeg".equals(type) || "image/bmp".equals(type) ||
                                            "image/gif".equals(type) || "image/jpg".equals(type) ||
                                            "image/png".equals(type)) {
                                        if (image == null)
                                        {
                                            image = "content://mms/part/" + partId;
                                        } else
                                        {
                                            image += " content://mms/part/" + partId;
                                        }
                                    }

                                    if ("video/mpeg".equals(type) || "video/3gpp".equals(type) || "video/mp4".equals(type))
                                    {
                                        video = "content://mms/part/" + partId;
                                    }

                                    if (type.startsWith("audio/")) {
                                        audio = "content://mms/part/" + partId;
                                    }
                                } while (query.moveToNext());
                            }

                            query.close();

                            if (image == null && video == null && audio == null && body.equals("")) {
                                context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                    @Override
                                    public void run() {
                                        downloadableMessage(holder, idF);
                                    }
                                });
                            } else {
                                String images[];
                                Bitmap picture;

                                try {
                                    holder.imageUri = Uri.parse(image);
                                    images = image.trim().split(" ");
                                    picture = BitmapFactory.decodeFile(getRealPathFromURI(Uri.parse(images[0])));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    images = null;
                                    picture = null;
                                } catch (Error e) {
                                    try {
                                        holder.imageUri = Uri.parse(image);
                                        images = image.trim().split(" ");
                                        int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, context.getResources().getDisplayMetrics());
                                        picture = decodeSampledBitmapFromFile(getRealPathFromURI(Uri.parse(images[0])), scale, scale);
                                    } catch (Exception f) {
                                        images = null;
                                        picture = null;
                                    }
                                }

                                final String text = body;
                                final String imageUri = image;
                                final String[] imagesF = images;
                                final Bitmap pictureF = picture;
                                final String videoF = video;
                                final String audioF = audio;

                                if (holder.text.getText().toString().equals("")) {
                                    // view is empty and has not been recycled, so show the images
                                    context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                        @Override
                                        public void run() {
                                            setMessageText(holder.text, text);

                                            if (imageUri == null && videoF == null && audioF == null) {
                                                holder.media.setVisibility(View.GONE);
                                                holder.media.setImageResource(android.R.color.transparent);
                                            } else if (imageUri != null) {
                                                holder.media.setVisibility(View.VISIBLE);

                                                if (pictureF == null) {
                                                    holder.media.setImageURI(Uri.parse(imagesF[0]));
                                                } else {
                                                    holder.media.setImageBitmap(pictureF);
                                                }

                                                holder.media.setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        if (imagesF.length == 1) {
                                                            Intent intent = new Intent();
                                                            intent.setAction(Intent.ACTION_VIEW);
                                                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                            intent.putExtra("SingleItemOnly", true);
                                                            intent.setDataAndType(Uri.parse(imageUri), "image/*");
                                                            context.startActivity(intent);
                                                        } else {
                                                            Intent intent = new Intent();
                                                            intent.setClass(context, ImageViewer.class);
                                                            Bundle b = new Bundle();
                                                            b.putString("image", imageUri);
                                                            intent.putExtra("bundle", b);
                                                            context.startActivity(intent);
                                                        }
                                                    }
                                                });

                                                if (imagesF.length > 1) {
                                                    holder.date.setText(holder.date.getText().toString() + " - Multiple Attachments");
                                                }
                                            } else if (videoF != null) {
                                                holder.media.setVisibility(View.VISIBLE);
                                                holder.media.setImageResource(R.drawable.ic_video_play);
                                                holder.media.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                                                holder.media.setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        Intent intent = new Intent();
                                                        intent.setAction(Intent.ACTION_VIEW);
                                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                        intent.setDataAndType(Uri.parse(videoF), "video/*");
                                                        context.startActivity(intent);
                                                    }
                                                });
                                            } else if (audioF != null) {
                                                holder.media.setVisibility(View.VISIBLE);
                                                holder.media.setImageResource(R.drawable.ic_video_play);
                                                holder.media.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                                                holder.media.setOnClickListener(new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(View view) {
                                                        Intent intent = new Intent();
                                                        intent.setAction(Intent.ACTION_VIEW);
                                                        intent.setDataAndType(Uri.parse(audioF), "audio/*");
                                                        context.startActivity(intent);
                                                    }
                                                });
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }).start();
                } else {
                    body = "";
                    image = null;
                    String video = null;
                    String audio = null;

                    Uri uri = Uri.parse("content://mms/part");
                    Cursor query = contentResolver.query(uri, null, selectionPart, null, null);

                    if (query.moveToFirst()) {
                        do {
                            String partId = query.getString(query.getColumnIndex("_id"));
                            String type = query.getString(query.getColumnIndex("ct"));
                            String body2 = "";

                            if ("text/plain".equals(type)) {
                                String data = query.getString(query.getColumnIndex("_data"));
                                if (data != null) {
                                    body2 = getMmsText(partId, context);
                                    body += body2;
                                } else {
                                    body2 = query.getString(query.getColumnIndex("text"));
                                    body += body2;
                                }
                            }

                            if ("image/jpeg".equals(type) || "image/bmp".equals(type) ||
                                    "image/gif".equals(type) || "image/jpg".equals(type) ||
                                    "image/png".equals(type)) {
                                if (image == null)
                                {
                                    image = "content://mms/part/" + partId;
                                } else
                                {
                                    image += " content://mms/part/" + partId;
                                }
                            }

                            if ("video/mpeg".equals(type) || "video/3gpp".equals(type) || "video/mp4".equals(type))
                            {
                                video = "content://mms/part/" + partId;
                            }

                            if (type.startsWith("audio/")) {
                                audio = "content://mms/part/" + partId;
                            }
                        } while (query.moveToNext());
                    }

                    query.close();

                    if (image == null && video == null && audio == null && body.equals("")) {
                        downloadableMessage(holder, id);
                    } else {
                        String images[];

                        try {
                            holder.imageUri = Uri.parse(image);
                            images = image.trim().split(" ");
                        } catch (Exception e) {
                            images = null;
                        }

                        final String text = body;
                        final String imageUri = images[0];
                        final String[] imagesF = images;
                        final String videoF = video;
                        final String audioF = audio;

                        setMessageText(holder.text, text);

                        if (imageUri == null && videoF == null && audioF == null) {
                            holder.media.setVisibility(View.GONE);
                            holder.media.setImageResource(android.R.color.transparent);
                        } else if (imageUri != null) {
                            holder.media.setVisibility(View.VISIBLE);
                            holder.media.setImageURI(Uri.parse(imageUri));
                            holder.media.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (imagesF.length == 1) {
                                        Intent intent = new Intent();
                                        intent.setAction(Intent.ACTION_VIEW);
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        intent.putExtra("SingleItemOnly", true);
                                        intent.setDataAndType(Uri.parse(imageUri), "image/*");
                                        context.startActivity(intent);
                                    } else {
                                        Intent intent = new Intent();
                                        intent.setClass(context, ImageViewer.class);
                                        Bundle b = new Bundle();
                                        b.putString("image", imageUri);
                                        intent.putExtra("bundle", b);
                                        context.startActivity(intent);
                                    }
                                }
                            });

                            if (imagesF.length > 1) {
                                holder.date.setText(holder.date.getText().toString() + " - Multiple Attachments");
                            }
                        } else if (videoF != null) {
                            holder.media.setVisibility(View.VISIBLE);
                            holder.media.setImageResource(R.drawable.ic_video_play);
                            holder.media.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            holder.media.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    intent.setDataAndType(Uri.parse(videoF), "video/*");
                                    context.startActivity(intent);
                                }
                            });
                        } else if (audioF != null) {
                            holder.media.setVisibility(View.VISIBLE);
                            holder.media.setImageResource(R.drawable.ic_video_play);
                            holder.media.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            holder.media.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Intent intent = new Intent();
                                    intent.setAction(Intent.ACTION_VIEW);
                                    intent.setDataAndType(Uri.parse(audioF), "audio/*");
                                    context.startActivity(intent);
                                }
                            });
                        }
                    }
                }

            } else {
                String type = cursor.getString(cursor.getColumnIndex("type"));

                if (type.equals("1"))
                {
                    sent = false;

                    try
                    {
                        body = cursor.getString(cursor.getColumnIndex("body"));
                    } catch (Exception e)
                    {
                        body = "";
                    }

                    date = cursor.getString(cursor.getColumnIndex(dateType));
                    id = cursor.getString(cursor.getColumnIndex("_id"));
                    mms = false;
                    image = null;

                    if (cursor.getInt(cursor.getColumnIndex("read")) == 0)
                    {
                        String SmsMessageId = cursor.getString(cursor.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("read", true);
                        contentResolver.update(Uri.parse("content://sms/inbox"), values, "_id=" + SmsMessageId, null);
                    }
                } else if (type.equals("2"))
                {
                    sent = true;
                    body = cursor.getString(cursor.getColumnIndex("body"));
                    date = cursor.getString(cursor.getColumnIndex("date"));
                    id = cursor.getString(cursor.getColumnIndex("_id"));
                    mms = false;
                    image = null;

                    if (sharedPrefs.getBoolean("delivery_reports", false)) {
                        status = cursor.getString(cursor.getColumnIndex("status"));

                        if (status.equals("64") || status.equals("128"))
                        {
                            error = true;
                        }
                    }

                    if (cursor.getInt(cursor.getColumnIndex("read")) == 0)
                    {
                        String SmsMessageId = cursor.getString(cursor.getColumnIndex("_id"));
                        ContentValues values = new ContentValues();
                        values.put("read", true);
                        contentResolver.update(Uri.parse("content://mms/inbox"), values, "_id=" + SmsMessageId, null);
                    }
                } else if (type.equals("5"))
                {
                    sent = true;
                    body = cursor.getString(cursor.getColumnIndex("body"));
                    date = cursor.getString(cursor.getColumnIndex("date"));
                    id = cursor.getString(cursor.getColumnIndex("_id"));
                    mms = false;
                    image = null;
                    error = true;
                } else if (type.equals("4") || type.equals("6"))
                {
                    sent = true;
                    body = cursor.getString(cursor.getColumnIndex("body"));
                    date = cursor.getString(cursor.getColumnIndex("date"));
                    id = cursor.getString(cursor.getColumnIndex("_id"));
                    mms = false;
                    image = null;
                    sending = true;
                } else
                {
                    sent = false;
                    body = cursor.getString(cursor.getColumnIndex("body"));
                    date = cursor.getString(cursor.getColumnIndex(dateType));
                    id = cursor.getString(cursor.getColumnIndex("_id"));
                    mms = false;
                    image = null;
                }
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        if (group && !sent)
        {
            final String sentFrom = sender;
            new Thread(new Runnable() {

                @Override
                public void run()
                {
                    final Bitmap picture = Bitmap.createScaledBitmap(getFacebookPhoto(sentFrom), MainActivity.contactWidth, MainActivity.contactWidth, true);

                    context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                        @Override
                        public void run() {
                            holder.image.setImageBitmap(picture);
                            holder.image.assignContactFromPhone(sentFrom, true);
                        }
                    });
                }

            }).start();
        }

        Date date2;

        try
        {
            date2 = new Date(Long.parseLong(date));
        } catch (Exception e)
        {
            date2 = new Date(0);
        }

        Calendar cal = Calendar.getInstance();
        Date currentDate = new Date(cal.getTimeInMillis());

        if (getZeroTimeDate(date2).equals(getZeroTimeDate(currentDate)))
        {
            if (sharedPrefs.getBoolean("hour_format", false))
            {
                holder.date.setText(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date2));
            } else
            {
                holder.date.setText(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date2));
            }
        } else
        {
            if (sharedPrefs.getBoolean("hour_format", false))
            {
                holder.date.setText(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2));
            } else
            {
                holder.date.setText(DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2));
            }
        }

        if (sending == true)
        {
            holder.date.setVisibility(View.GONE);

            try
            {
                holder.ellipsis.setVisibility(View.VISIBLE);
                holder.ellipsis.setBackgroundResource(R.drawable.ellipsis);
                holder.ellipsis.setColorFilter(sharedPrefs.getInt("ct_receivedTextColor", context.getResources().getColor(R.color.black)));
                AnimationDrawable ellipsis = (AnimationDrawable) holder.ellipsis.getBackground();
                ellipsis.start();
            } catch (Exception e)
            {

            }
        } else
        {
            holder.date.setVisibility(View.VISIBLE);

            try
            {
                holder.ellipsis.setVisibility(View.GONE);
            } catch (Exception e)
            {

            }

            if (sent && sharedPrefs.getBoolean("delivery_reports", false) && !error && status.equals("0"))
            {
                String text = "<html><body><img src=\"ic_sent.png\"/> " + holder.date.getText().toString() + "</body></html>";
                holder.date.setText(Html.fromHtml(text, imgGetterSent, null));
            } else if (error) {
                String text = "<html><body><img src=\"ic_error.png\"/> ERROR</body></html>";
                holder.date.setText(Html.fromHtml(text, imgGetterFail, null));
            }
        }

        if (group == true && sent == false)
        {
            final String senderF = sender;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(senderF.replaceAll("-", "")));
                    final Cursor phonesCursor = context.getContentResolver().query(phoneUri, new String[] {ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.RawContacts._ID}, null, null, ContactsContract.Contacts.DISPLAY_NAME + " desc limit 1");

                    context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                        @Override
                        public void run() {
                            if(phonesCursor != null && phonesCursor.moveToFirst()) {
                                holder.date.setText(holder.date.getText() + " - " + phonesCursor.getString(0));
                            } else
                            {
                                holder.date.setText(holder.date.getText() + " - " + senderF);
                            }

                            phonesCursor.close();
                        }

                    });
                }
            }).start();
        }

        setMessageText(holder.text, body);

        if (cursor.getPosition() == 0)
        {
            if (sharedPrefs.getString("run_as", "sliding").equals("hangout")) {
                int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7, context.getResources().getDisplayMetrics());
                int scale2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                int scale3 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
                int scale4 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, context.getResources().getDisplayMetrics());
                
                if (sent) {
                    view.setPadding(scale4, scale2, scale, scale3);
                } else {
                    view.setPadding(scale, scale2, scale4, scale3);
                }
            } else if (sharedPrefs.getString("run_as", "sliding").equals("card2")) {
                int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 17, context.getResources().getDisplayMetrics());
                int scale2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
                int scale3 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                view.setPadding(scale, scale2, scale, scale3);
            } else {
                int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                view.setPadding(0, 0, 0, scale);
            }
        } else {
            if (sharedPrefs.getString("run_as", "sliding").equals("hangout")) {
                int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7, context.getResources().getDisplayMetrics());
                int scale2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
                int scale3 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, context.getResources().getDisplayMetrics());
            
                if (sent) {
                    view.setPadding(scale3, scale2, scale, scale2);
                } else {
                    view.setPadding(scale, scale2, scale3, scale2);
                }
            } else if (sharedPrefs.getString("run_as", "sliding").equals("card2")) {
                int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 17, context.getResources().getDisplayMetrics());
                int scale2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
                view.setPadding(scale, scale2, scale, 0);
            } else {
                view.setPadding(0, 0, 0, 0);
            }
        }

        final String dateT = date;
        int size2 = 0;

        try
        {
            size2 = image.split(" ").length;
        } catch (Exception e)
        {

        }

        final View rowViewF = view;

        if (mms) {
            holder.media.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    rowViewF.performLongClick();
                    return true;
                }
            });
        }
        
        holder.text.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                rowViewF.performLongClick();
                return true;
            }
        });

        final String idF = id;
        final boolean mmsF = mms;
        final boolean sentF = sent;

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mmsF)
                {
                    Cursor query;
                    String dialogText = "";

                    try
                    {
                        if (!sentF)
                        {
                            query = contentResolver.query(Uri.parse("content://sms/" + idF + "/"), new String[] {"date", "date_sent", "type", "address"}, null, null, "date desc limit 1");

                            if (query.moveToFirst())
                            {
                                String dateSent = query.getString(query.getColumnIndex("date_sent")), dateReceived = query.getString(query.getColumnIndex("date"));
                                Date date1 = new Date(Long.parseLong(dateSent)), date2 = new Date(Long.parseLong(dateReceived));

                                if (sharedPrefs.getBoolean("hour_format", false))
                                {
                                    dateSent = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date1) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date1);
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                } else
                                {
                                    dateSent = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date1) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date1);
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                }

                                dialogText = context.getResources().getString(R.string.type) + " Text Message\n" +
                                        context.getResources().getString(R.string.from) + " " + query.getString(query.getColumnIndex("address")) + "\n" +
                                        context.getResources().getString(R.string.sent) + " " + dateSent + "\n" +
                                        context.getResources().getString(R.string.received) + " " + dateReceived;
                            }
                        } else
                        {
                            query = contentResolver.query(Uri.parse("content://sms/" + idF + "/"), new String[] {"date", "status", "type", "address"}, null, null, "date desc limit 1");

                            if (query.moveToFirst())
                            {
                                String dateReceived = query.getString(query.getColumnIndex("date"));
                                Date date2 = new Date(Long.parseLong(dateReceived));

                                if (sharedPrefs.getBoolean("hour_format", false))
                                {
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                } else
                                {
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                }

                                dialogText = context.getResources().getString(R.string.type) + " Text Message\n" +
                                        context.getResources().getString(R.string.to) + " " + query.getString(query.getColumnIndex("address")) + "\n" +
                                        context.getResources().getString(R.string.sent) + " " + dateReceived;

                                String status = query.getString(query.getColumnIndex("status"));

                                if (!status.equals("-1"))
                                {
                                    if (status.equals("64") || status.equals("128"))
                                    {
                                        dialogText += "\n" + context.getResources().getString(R.string.status) + " Error";
                                    } else
                                    {
                                        dialogText += "\n" + context.getResources().getString(R.string.status) + " Delivered";
                                    }
                                }
                            }
                        }
                    } catch (Exception e)
                    {
                        query = contentResolver.query(Uri.parse("content://sms/" + idF + "/"), new String[] {"date", "status", "type", "address"}, null, null, "date desc limit 1");

                        if (query.moveToFirst())
                        {
                            if (sentF)
                            {
                                String dateReceived = query.getString(query.getColumnIndex("date"));
                                Date date2 = new Date(Long.parseLong(dateReceived));

                                if (sharedPrefs.getBoolean("hour_format", false))
                                {
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                } else
                                {
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                }

                                dialogText = context.getResources().getString(R.string.type) + " Text Message\n" +
                                        context.getResources().getString(R.string.to) + " " + query.getString(query.getColumnIndex("address")) + "\n" +
                                        context.getResources().getString(R.string.sent) + " " + dateReceived;

                                String status = query.getString(query.getColumnIndex("status"));

                                if (!status.equals("-1"))
                                {
                                    if (status.equals("64") || status.equals("128"))
                                    {
                                        dialogText += "\n" + context.getResources().getString(R.string.status) + " Error";
                                    } else
                                    {
                                        dialogText += "\n" + context.getResources().getString(R.string.status) + " Delivered";
                                    }
                                }
                            } else
                            {
                                String dateReceived = query.getString(query.getColumnIndex("date"));
                                Date date2 = new Date(Long.parseLong(dateReceived));

                                if (sharedPrefs.getBoolean("hour_format", false))
                                {
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.GERMAN).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                } else
                                {
                                    dateReceived = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.US).format(date2) + ", " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(date2);
                                }

                                dialogText = context.getResources().getString(R.string.type) + " Text Message\n" +
                                        context.getResources().getString(R.string.from) + " " + query.getString(query.getColumnIndex("address")) + "\n" +
                                        context.getResources().getString(R.string.received) + " " + dateReceived;
                            }
                        }
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(context.getResources().getString(R.string.message_details));
                    builder.setMessage(dialogText);
                    builder.create().show();
                }
            }
        });

        final int sizeT = size2;
        final boolean errorT = error;

        view.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(final View arg0) {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(25);

                AlertDialog.Builder builder2 = new AlertDialog.Builder(context);

                if (!errorT)
                {
                    if (!mmsF || sizeT > 1)
                    {
                        builder2.setItems(R.array.messageOptions, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which)
                                {
                                    case 0:
                                        TextView tv = (TextView) arg0.findViewById(R.id.textBody);
                                        ClipboardManager clipboard = (ClipboardManager)
                                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                                        ClipData clip = ClipData.newPlainText("Copied Message", tv.getText().toString());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(context, R.string.text_saved, Toast.LENGTH_SHORT).show();
                                        break;
                                    case 1:
                                        MainActivity.menu.showSecondaryMenu();

                                        View newMessageView = MainActivity.menu.getSecondaryMenu();

                                        EditText body = (EditText) newMessageView.findViewById(R.id.messageEntry2);
                                        TextView tv2 = (TextView) arg0.findViewById(R.id.textBody);

                                        body.setText(tv2.getText().toString());

                                        break;
                                    case 2:
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setMessage(context.getResources().getString(R.string.delete_message));
                                        builder.setPositiveButton(context.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                                            @SuppressLint("SimpleDateFormat")
                                            public void onClick(DialogInterface dialog, int id) {
                                                String threadId = threadIds;

                                                deleteSMS(context, threadId, idF);
                                                ((MainActivity) context).refreshViewPager(true);
                                            }

                                            public void deleteSMS(Context context, String threadId, String messageId) {
                                                try {
                                                    context.getContentResolver().delete(Uri.parse("content://mms-sms/conversations/" + threadId + "/"), "_id=" + messageId, null);
                                                } catch (Exception e) {
                                                }
                                            }});
                                        builder.setNegativeButton(context.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                            }
                                        });
                                        AlertDialog dialog2 = builder.create();

                                        dialog2.show();
                                        break;
                                    default:
                                        break;
                                }

                            }

                        });

                        AlertDialog dialog = builder2.create();
                        dialog.show();
                    } else
                    {
                        builder2.setItems(R.array.messageOptions2, new DialogInterface.OnClickListener() {

                            @SuppressWarnings("deprecation")
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which)
                                {
                                    case 0:
                                        TextView tv = (TextView) arg0.findViewById(R.id.textBody);
                                        ClipboardManager clipboard = (ClipboardManager)
                                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                                        ClipData clip = ClipData.newPlainText("Copied Message", tv.getText().toString());
                                        clipboard.setPrimaryClip(clip);

                                        Toast.makeText(context, R.string.text_saved, Toast.LENGTH_SHORT).show();
                                        break;
                                    case 1:
                                        try {
                                            saveImage(((BitmapDrawable) holder.media.getDrawable()).getBitmap(), dateT);
                                        } catch (Exception e)
                                        {
                                            Toast.makeText(context, "ERROR", Toast.LENGTH_SHORT).show();
                                        }
                                        break;
                                    case 2:
                                        MainActivity.menu.showSecondaryMenu();

                                        View newMessageView = MainActivity.menu.getSecondaryMenu();

                                        EditText body = (EditText) newMessageView.findViewById(R.id.messageEntry2);
                                        TextView tv2 = (TextView) arg0.findViewById(R.id.textBody);

                                        try
                                        {
                                            ((MainActivity)context).attachedImage2 = holder.imageUri;

                                            ((MainActivity)context).imageAttachBackground2.setBackgroundColor(sharedPrefs.getInt("ct_conversationListBackground", context.getResources().getColor(R.color.light_silver)));
                                            Drawable attachBack = context.getResources().getDrawable(R.drawable.attachment_editor_bg);
                                            attachBack.setColorFilter(sharedPrefs.getInt("ct_sentMessageBackground", context.getResources().getColor(R.color.white)), PorterDuff.Mode.MULTIPLY);
                                            ((MainActivity)context).imageAttach2.setBackgroundDrawable(attachBack);
                                            ((MainActivity)context).imageAttachBackground2.setVisibility(View.VISIBLE);
                                            ((MainActivity)context).imageAttach2.setVisibility(true);
                                        } catch (Exception e)
                                        {

                                        }

                                        try
                                        {
                                            ((MainActivity)context).imageAttach2.setImage("send_image", decodeFile(new File(getPath(holder.imageUri))));
                                        } catch (Exception e)
                                        {
                                            ((MainActivity)context).imageAttach2.setVisibility(false);
                                            ((MainActivity)context).imageAttachBackground2.setVisibility(View.GONE);
                                        }

                                        Button viewImage = (Button) newMessageView.findViewById(R.id.view_image_button2);
                                        Button replaceImage = (Button) newMessageView.findViewById(R.id.replace_image_button2);
                                        Button removeImage = (Button) newMessageView.findViewById(R.id.remove_image_button2);

                                        viewImage.setOnClickListener(new View.OnClickListener() {

                                            @Override
                                            public void onClick(View arg0) {
                                                context.startActivity(new Intent(Intent.ACTION_VIEW, holder.imageUri));

                                            }

                                        });

                                        replaceImage.setOnClickListener(new View.OnClickListener() {

                                            @Override
                                            public void onClick(View v) {
                                                Intent intent = new Intent();
                                                intent.setType("image/*");
                                                intent.setAction(Intent.ACTION_GET_CONTENT);
                                                context.startActivityForResult(Intent.createChooser(intent, context.getResources().getString(R.string.select_picture)), 2);

                                            }

                                        });

                                        removeImage.setOnClickListener(new View.OnClickListener() {

                                            @Override
                                            public void onClick(View v) {
                                                ((MainActivity)context).imageAttach2.setVisibility(false);
                                                ((MainActivity)context).imageAttachBackground2.setVisibility(View.GONE);

                                            }

                                        });

                                        body.setText(tv2.getText().toString());

                                        try
                                        {

                                        } catch (Exception e)
                                        {

                                        }

                                        break;
                                    case 3:
                                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                        builder.setMessage(context.getResources().getString(R.string.delete_message));
                                        builder.setPositiveButton(context.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                                            @SuppressLint("SimpleDateFormat")
                                            public void onClick(DialogInterface dialog, int id) {
                                                String threadId = threadIds;

                                                deleteSMS(context, threadId, idF);
                                                ((MainActivity) context).refreshViewPager(true);
                                            }

                                            public void deleteSMS(Context context, String threadId, String messageId) {
                                                try {
                                                    context.getContentResolver().delete(Uri.parse("content://mms-sms/conversations/" + threadId + "/"), "_id=" + messageId, null);
                                                } catch (Exception e) {
                                                }
                                            }});
                                        builder.setNegativeButton(context.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                dialog.dismiss();
                                            }
                                        });
                                        AlertDialog dialog2 = builder.create();

                                        dialog2.show();
                                        break;
                                    default:
                                        break;
                                }

                            }

                        });

                        AlertDialog dialog = builder2.create();
                        dialog.show();
                    }
                } else
                {
                    builder2.setItems(R.array.messageOptions3, new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (which)
                            {
                                case 0:
                                    if (!mmsF)
                                    {
                                        MainActivity.animationOn = true;

                                        String body2 = ((TextView) arg0.findViewById(R.id.textBody)).getText().toString();

                                        if (!sharedPrefs.getString("signature", "").equals(""))
                                        {
                                            body2 += "\n" + sharedPrefs.getString("signature", "");
                                        }

                                        final String body = body2;

                                        new Thread(new Runnable() {

                                            @Override
                                            public void run() {

                                                if (sharedPrefs.getBoolean("delivery_reports", false))
                                                {
                                                    if (inboxNumbers.replaceAll("[^0-9]", "").equals(""))
                                                    {
                                                        String SENT = "SMS_SENT";
                                                        String DELIVERED = "SMS_DELIVERED";

                                                        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0,
                                                                new Intent(SENT), 0);

                                                        PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0,
                                                                new Intent(DELIVERED), 0);

                                                        //---when the SMS has been sent---
                                                        context.registerReceiver(new BroadcastReceiver(){
                                                            @Override
                                                            public void onReceive(Context arg0, Intent arg1) {
                                                                try {
                                                                    switch (getResultCode())
                                                                    {
                                                                        case Activity.RESULT_OK:
                                                                            Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "2");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            NotificationCompat.Builder mBuilder =
                                                                                    new NotificationCompat.Builder(context)
                                                                                            .setSmallIcon(R.drawable.ic_alert)
                                                                                            .setContentTitle("Error")
                                                                                            .setContentText("Could not send message");

                                                                            Intent resultIntent = new Intent(context, MainActivity.class);

                                                                            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                                                                            stackBuilder.addParentStack(MainActivity.class);
                                                                            stackBuilder.addNextIntent(resultIntent);
                                                                            PendingIntent resultPendingIntent =
                                                                                    stackBuilder.getPendingIntent(
                                                                                            0,
                                                                                            PendingIntent.FLAG_UPDATE_CURRENT
                                                                                    );

                                                                            mBuilder.setContentIntent(resultPendingIntent);
                                                                            mBuilder.setAutoCancel(true);
                                                                            long[] pattern = {0L, 400L, 100L, 400L};
                                                                            mBuilder.setVibrate(pattern);
                                                                            mBuilder.setLights(0xFFffffff, 1000, 2000);

                                                                            try
                                                                            {
                                                                                mBuilder.setSound(Uri.parse(sharedPrefs.getString("ringtone", "null")));
                                                                            } catch(Exception e)
                                                                            {
                                                                                mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                                                                            }

                                                                            NotificationManager mNotificationManager =
                                                                                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                                                                            Notification notification = mBuilder.build();
                                                                            Intent deleteIntent = new Intent(context, NotificationReceiver.class);
                                                                            notification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
                                                                            mNotificationManager.notify(1, notification);
                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            Toast.makeText(context, "No service",
                                                                                    Toast.LENGTH_SHORT).show();
                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_NULL_PDU:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            Toast.makeText(context, "Null PDU",
                                                                                    Toast.LENGTH_SHORT).show();
                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            Toast.makeText(context, "Radio off",
                                                                                    Toast.LENGTH_SHORT).show();
                                                                            break;
                                                                    }

                                                                    context.unregisterReceiver(this);
                                                                } catch (Exception e) {

                                                                }
                                                            }
                                                        }, new IntentFilter(SENT));

                                                        //---when the SMS has been delivered---
                                                        context.registerReceiver(new BroadcastReceiver(){
                                                            @Override
                                                            public void onReceive(Context arg0, Intent arg1) {
                                                                try {
                                                                    if (sharedPrefs.getString("delivery_options", "2").equals("1"))
                                                                    {
                                                                        switch (getResultCode())
                                                                        {
                                                                            case Activity.RESULT_OK:
                                                                                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                                                                builder.setMessage(R.string.message_delivered)
                                                                                        .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                                                                                            public void onClick(DialogInterface dialog, int id) {
                                                                                                dialog.dismiss();
                                                                                            }
                                                                                        });

                                                                                builder.create().show();

                                                                                Cursor query = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                                                                                if (query.moveToFirst())
                                                                                {
                                                                                    String id = query.getString(query.getColumnIndex("_id"));
                                                                                    ContentValues values = new ContentValues();
                                                                                    values.put("status", "0");
                                                                                    context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                                                                                    ((MainActivity) context).refreshViewPager3();
                                                                                }
                                                                                break;
                                                                            case Activity.RESULT_CANCELED:
                                                                                AlertDialog.Builder builder2 = new AlertDialog.Builder(context);
                                                                                builder2.setMessage(R.string.message_not_delivered)
                                                                                        .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                                                                                            public void onClick(DialogInterface dialog, int id) {
                                                                                                dialog.dismiss();
                                                                                            }
                                                                                        });

                                                                                builder2.create().show();

                                                                                Cursor query2 = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                                                                                if (query2.moveToFirst())
                                                                                {
                                                                                    String id = query2.getString(query2.getColumnIndex("_id"));
                                                                                    ContentValues values = new ContentValues();
                                                                                    values.put("status", "64");
                                                                                    context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                                                                                    ((MainActivity) context).refreshViewPager3();
                                                                                }
                                                                                break;
                                                                        }
                                                                    } else
                                                                    {
                                                                        switch (getResultCode())
                                                                        {
                                                                            case Activity.RESULT_OK:
                                                                                if (sharedPrefs.getString("delivery_options", "2").equals("2"))
                                                                                {
                                                                                    Toast.makeText(context, R.string.message_delivered, Toast.LENGTH_LONG).show();
                                                                                }

                                                                                Cursor query = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                                                                                if (query.moveToFirst())
                                                                                {
                                                                                    String id = query.getString(query.getColumnIndex("_id"));
                                                                                    ContentValues values = new ContentValues();
                                                                                    values.put("status", "0");
                                                                                    context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                                                                                    ((MainActivity) context).refreshViewPager3();
                                                                                }

                                                                                break;
                                                                            case Activity.RESULT_CANCELED:
                                                                                if (sharedPrefs.getString("delivery_options", "2").equals("2"))
                                                                                {
                                                                                    Toast.makeText(context, R.string.message_not_delivered, Toast.LENGTH_LONG).show();
                                                                                }

                                                                                Cursor query2 = context.getContentResolver().query(Uri.parse("content://sms/sent"), null, null, null, "date desc");

                                                                                if (query2.moveToFirst())
                                                                                {
                                                                                    String id = query2.getString(query2.getColumnIndex("_id"));
                                                                                    ContentValues values = new ContentValues();
                                                                                    values.put("status", "64");
                                                                                    context.getContentResolver().update(Uri.parse("content://sms/sent"), values, "_id=" + id, null);
                                                                                    ((MainActivity) context).refreshViewPager3();
                                                                                }
                                                                                break;
                                                                        }
                                                                    }

                                                                    context.unregisterReceiver(this);
                                                                } catch (Exception e) {

                                                                }
                                                            }
                                                        }, new IntentFilter(DELIVERED));

                                                        ArrayList<PendingIntent> sPI = new ArrayList<PendingIntent>();
                                                        ArrayList<PendingIntent> dPI = new ArrayList<PendingIntent>();

                                                        String body2 = body;

                                                        if (sharedPrefs.getBoolean("strip_unicode", false))
                                                        {
                                                            body2 = StripAccents.stripAccents(body2);
                                                        }

                                                        SmsManager smsManager = SmsManager.getDefault();
                                                        ArrayList<String> parts = smsManager.divideMessage(body2);

                                                        for (int i = 0; i < parts.size(); i++)
                                                        {
                                                            sPI.add(sentPI);
                                                            dPI.add(deliveredPI);
                                                        }

                                                        smsManager.sendMultipartTextMessage(inboxNumbers, null, parts, sPI, dPI);
                                                    } else
                                                    {
                                                    }
                                                } else
                                                {
                                                    if (!inboxNumbers.replaceAll("[^0-9]", "").equals(""))
                                                    {
                                                        String SENT = "SMS_SENT";

                                                        PendingIntent sentPI = PendingIntent.getBroadcast(context, 0,
                                                                new Intent(SENT), 0);

                                                        //---when the SMS has been sent---
                                                        context.registerReceiver(new BroadcastReceiver(){
                                                            @Override
                                                            public void onReceive(Context arg0, Intent arg1) {
                                                                try {
                                                                    switch (getResultCode())
                                                                    {
                                                                        case Activity.RESULT_OK:
                                                                            Cursor query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "2");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            NotificationCompat.Builder mBuilder =
                                                                                    new NotificationCompat.Builder(context)
                                                                                            .setSmallIcon(R.drawable.ic_alert)
                                                                                            .setContentTitle("Error")
                                                                                            .setContentText("Could not send message");

                                                                            Intent resultIntent = new Intent(context, MainActivity.class);

                                                                            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
                                                                            stackBuilder.addParentStack(MainActivity.class);
                                                                            stackBuilder.addNextIntent(resultIntent);
                                                                            PendingIntent resultPendingIntent =
                                                                                    stackBuilder.getPendingIntent(
                                                                                            0,
                                                                                            PendingIntent.FLAG_UPDATE_CURRENT
                                                                                    );

                                                                            mBuilder.setContentIntent(resultPendingIntent);
                                                                            mBuilder.setAutoCancel(true);
                                                                            long[] pattern = {0L, 400L, 100L, 400L};
                                                                            mBuilder.setVibrate(pattern);
                                                                            mBuilder.setLights(0xFFffffff, 1000, 2000);

                                                                            try
                                                                            {
                                                                                mBuilder.setSound(Uri.parse(sharedPrefs.getString("ringtone", "null")));
                                                                            } catch(Exception e)
                                                                            {
                                                                                mBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                                                                            }

                                                                            NotificationManager mNotificationManager =
                                                                                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                                                                            Notification notification = mBuilder.build();
                                                                            Intent deleteIntent = new Intent(context, NotificationReceiver.class);
                                                                            notification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
                                                                            mNotificationManager.notify(1, notification);
                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            Toast.makeText(context, "No service",
                                                                                    Toast.LENGTH_SHORT).show();
                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_NULL_PDU:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            Toast.makeText(context, "Null PDU",
                                                                                    Toast.LENGTH_SHORT).show();
                                                                            break;
                                                                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                                                                            query = context.getContentResolver().query(Uri.parse("content://sms/outbox"), null, null, null, null);

                                                                            if (query.moveToFirst())
                                                                            {
                                                                                String id = query.getString(query.getColumnIndex("_id"));
                                                                                ContentValues values = new ContentValues();
                                                                                values.put("type", "5");
                                                                                context.getContentResolver().update(Uri.parse("content://sms/outbox"), values, "_id=" + id, null);
                                                                                ((MainActivity) context).refreshViewPager3();
                                                                            }

                                                                            Toast.makeText(context, "Radio off",
                                                                                    Toast.LENGTH_SHORT).show();
                                                                            break;
                                                                    }

                                                                    context.unregisterReceiver(this);
                                                                } catch (Exception e) {

                                                                }
                                                            }
                                                        }, new IntentFilter(SENT));

                                                        ArrayList<PendingIntent> sPI = new ArrayList<PendingIntent>();

                                                        String body2 = body;

                                                        if (sharedPrefs.getBoolean("strip_unicode", false))
                                                        {
                                                            body2 = StripAccents.stripAccents(body2);
                                                        }

                                                        SmsManager smsManager = SmsManager.getDefault();
                                                        ArrayList<String> parts = smsManager.divideMessage(body2);

                                                        for (int i = 0; i < parts.size(); i++)
                                                        {
                                                            sPI.add(sentPI);
                                                        }

                                                        smsManager.sendMultipartTextMessage(inboxNumbers, null, parts, sPI, null);
                                                    } else
                                                    {
                                                    }
                                                }

                                                String address = inboxNumbers;

                                                if (!address.replaceAll("[^0-9]", "").equals(""))
                                                {
                                                    final Calendar cal = Calendar.getInstance();
                                                    ContentValues values = new ContentValues();
                                                    values.put("address", address);
                                                    values.put("body", StripAccents.stripAccents(body));
                                                    values.put("date", cal.getTimeInMillis() + "");
                                                    values.put("thread_id", threadIds);
                                                    context.getContentResolver().insert(Uri.parse("content://sms/outbox"), values);

                                                    Cursor deleter = context.getContentResolver().query(Uri.parse("content://sms/failed"), null, null, null, null);

                                                    if (deleter.moveToFirst())
                                                    {
                                                        String id = deleter.getString(deleter.getColumnIndex("_id"));

                                                        context.getContentResolver().delete(Uri.parse("content://mms-sms/conversations/" + threadIds + "/"), "_id=" + id, null);
                                                    }

                                                    final String address2 = address;

                                                    context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                                        @Override
                                                        public void run() {
                                                            MainActivity.sentMessage = true;
                                                            ((MainActivity) context).refreshViewPager4(address2, StripAccents.stripAccents(body), cal.getTimeInMillis() + "");
                                                        }

                                                    });
                                                }
                                            }

                                        }).start();
                                    } else
                                    {
                                        Toast.makeText(context, "Cannot resend MMS, try making a new message", Toast.LENGTH_LONG).show();
                                    }

                                    break;
                                case 1:
                                    TextView tv = (TextView) arg0.findViewById(R.id.textBody);
                                    ClipboardManager clipboard = (ClipboardManager)
                                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                                    ClipData clip = ClipData.newPlainText("Copied Message", tv.getText().toString());
                                    clipboard.setPrimaryClip(clip);

                                    Toast.makeText(context, R.string.text_saved, Toast.LENGTH_SHORT).show();
                                    break;
                                case 2:
                                    MainActivity.menu.showSecondaryMenu();

                                    View newMessageView = MainActivity.menu.getSecondaryMenu();

                                    EditText body3 = (EditText) newMessageView.findViewById(R.id.messageEntry2);
                                    TextView tv2 = (TextView) arg0.findViewById(R.id.textBody);

                                    body3.setText(tv2.getText().toString());

                                    break;
                                case 3:
                                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                    builder.setMessage(context.getResources().getString(R.string.delete_message));
                                    builder.setPositiveButton(context.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
                                        @SuppressLint("SimpleDateFormat")
                                        public void onClick(DialogInterface dialog, int id) {
                                            String threadId = threadIds;

                                            deleteSMS(context, threadId, idF);
                                            ((MainActivity) context).refreshViewPager(true);
                                        }

                                        public void deleteSMS(Context context, String threadId, String messageId) {
                                            try {
                                                context.getContentResolver().delete(Uri.parse("content://mms-sms/conversations/" + threadId + "/"), "_id=" + messageId, null);
                                            } catch (Exception e) {
                                            }
                                        }});
                                    builder.setNegativeButton(context.getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            dialog.dismiss();
                                        }
                                    });
                                    AlertDialog dialog2 = builder.create();

                                    dialog2.show();
                                    break;
                                default:
                                    break;
                            }

                        }

                    });

                    AlertDialog dialog = builder2.create();
                    dialog.show();
                }

                return true;
            }

        });

        if (MainActivity.animationOn == true && cursor.getPosition() == 0 && threadPosition == 0)
        {
            String animation = sharedPrefs.getString("send_animation", "left");

            if (animation.equals("left"))
            {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_right);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            } else if (animation.equals("right"))
            {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_left);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            } else if (animation.equals("up"))
            {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_up);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            } else if (animation.equals("hangouts")) {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.hangouts_in);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            }

            MainActivity.animationOn = false;
        }

        if (MainActivity.animationReceived == 1 && cursor.getPosition() == 0 && MainActivity.animationThread == threadPosition)
        {
            String animation = sharedPrefs.getString("receive_animation", "right");

            if (animation.equals("left"))
            {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_right);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            } else if (animation.equals("right"))
            {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_in_left);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            } else if (animation.equals("up"))
            {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.slide_up);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            } else if (animation.equals("hangouts")) {
                Animation anim = AnimationUtils.loadAnimation(context, R.anim.hangouts_in);
                anim.setDuration(sharedPrefs.getInt("animation_speed", 300));
                view.startAnimation(anim);
            }

            MainActivity.animationReceived = 0;
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        ViewHolder holder = new ViewHolder();
        View v;

        int type = getItemViewType(cursor);

        if (type == 1) {
            if (sharedPrefs.getString("run_as", "sliding").equals("hangout")) {
                v = mInflater.inflate(R.layout.message_hangout_sent, parent, false);
            } else if (sharedPrefs.getString("run_as", "sliding").equals("sliding")) {
                v = mInflater.inflate(R.layout.message_classic_sent, parent, false);
            } else {
                v = mInflater.inflate(R.layout.message_card2_sent, parent, false);
            }

            holder.text = (TextView) v.findViewById(R.id.textBody);
            holder.date = (TextView) v.findViewById(R.id.textDate);
            holder.media = (ImageView) v.findViewById(R.id.media);
            holder.image = (QuickContactBadge) v.findViewById(R.id.imageContactPicture);
            holder.ellipsis = (ImageView) v.findViewById(R.id.ellipsis);
            holder.bubble = (ImageView) v.findViewById(R.id.msgBubble);
            holder.background = v.findViewById(R.id.messageBody);

            holder.image.assignContactUri(ContactsContract.Profile.CONTENT_URI);
        } else {
            if (sharedPrefs.getString("run_as", "sliding").equals("hangout")) {
                v = mInflater.inflate(R.layout.message_hangout_received, parent, false);
            } else if (sharedPrefs.getString("run_as", "sliding").equals("sliding")) {
                v = mInflater.inflate(R.layout.message_classic_received, parent, false);
            } else {
                v = mInflater.inflate(R.layout.message_card2_received, parent, false);
            }

            holder.text = (TextView) v.findViewById(R.id.textBody);
            holder.date = (TextView) v.findViewById(R.id.textDate);
            holder.media = (ImageView) v.findViewById(R.id.media);
            holder.image = (QuickContactBadge) v.findViewById(R.id.imageContactPicture);
            holder.downloadButton = (Button) v.findViewById(R.id.downloadButton);
            holder.bubble = (ImageView) v.findViewById(R.id.msgBubble);
            holder.background = v.findViewById(R.id.messageBody);

            holder.image.assignContactFromPhone(inboxNumbers, true);
        }

        if (sharedPrefs.getString("run_as", "sliding").equals("card2")) {
            String themeName = sharedPrefs.getString("ct_theme_name", "Light Theme");

            if (themeName.equals("Light Theme") || themeName.equals("Hangouts Theme") || themeName.equals("Light Theme 2.0") || themeName.equals("Light Green Theme") || themeName.equals("Burnt Orange Theme")) {

            } else {
                v.findViewById(R.id.shadow).setVisibility(View.GONE);
            }

            if (type == 1) {
                v.findViewById(R.id.divider).setBackgroundColor(convertToColorInt(convertToARGB(sharedPrefs.getInt("ct_sentTextColor", context.getResources().getColor(R.color.black)), "44")));
            } else {
                v.findViewById(R.id.divider).setBackgroundColor(convertToColorInt(convertToARGB(sharedPrefs.getInt("ct_receivedTextColor", context.getResources().getColor(R.color.black)), "44")));
            }
        }

        if (sharedPrefs.getBoolean("custom_font", false))
        {
            holder.text.setTypeface(font);
            holder.date.setTypeface(font);
        }

        if (sharedPrefs.getBoolean("contact_pictures", true))
        {
            if (type == 0)
            {
                holder.image.setImageBitmap(contactImage);
            } else
            {
                holder.image.setImageBitmap(myImage);
            }
        } else
        {
            holder.image.setMaxWidth(0);
            holder.image.setMinimumWidth(0);
        }

        try {
            holder.text.setTextSize(Integer.parseInt(sharedPrefs.getString("text_size", "14").substring(0,2)));
            holder.date.setTextSize(Integer.parseInt(sharedPrefs.getString("text_size", "14").substring(0,2)) - 4);
        } catch (Exception e) {
            holder.text.setTextSize(Integer.parseInt(sharedPrefs.getString("text_size", "14").substring(0,1)));
            holder.date.setTextSize(Integer.parseInt(sharedPrefs.getString("text_size", "14").substring(0,1)) - 4);
        }

        if (sharedPrefs.getBoolean("tiny_date", false))
        {
            holder.date.setTextSize(10);
        }

        holder.text.setText("");
        holder.date.setText("");

        if (type == 0) {
            holder.downloadButton.setVisibility(View.GONE);
        }

        if (type == 1) {
            holder.text.setTextColor(sharedPrefs.getInt("ct_sentTextColor", context.getResources().getColor(R.color.black)));
            holder.date.setTextColor(convertToColorInt(convertToARGB(sharedPrefs.getInt("ct_sentTextColor", context.getResources().getColor(R.color.black)), "55")));
            holder.background.setBackgroundColor(sharedPrefs.getInt("ct_sentMessageBackground", context.getResources().getColor(R.color.white)));
            holder.media.setBackgroundColor(sharedPrefs.getInt("ct_sentMessageBackground", context.getResources().getColor(R.color.white)));
            holder.bubble.setColorFilter(sharedPrefs.getInt("ct_sentMessageBackground", context.getResources().getColor(R.color.white)));

            if (!sharedPrefs.getBoolean("custom_theme", false))
            {
                String color = sharedPrefs.getString("sent_text_color", "default");

                if (color.equals("blue"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_blue));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_blue));
                } else if (color.equals("white"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.white));
                    holder.date.setTextColor(context.getResources().getColor(R.color.white));
                } else if (color.equals("green"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_green));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_green));
                } else if (color.equals("orange"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_orange));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_orange));
                } else if (color.equals("red"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_red));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_red));
                } else if (color.equals("purple"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_purple));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_purple));
                } else if (color.equals("black"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.pitch_black));
                    holder.date.setTextColor(context.getResources().getColor(R.color.pitch_black));
                } else if (color.equals("grey"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.grey));
                    holder.date.setTextColor(context.getResources().getColor(R.color.grey));
                }

                holder.background.setBackgroundColor(convertToColorInt(convertToARGB(sharedPrefs.getInt("ct_sentMessageBackground", context.getResources().getColor(R.color.black)), sharedPrefs.getInt("text_opacity", 100) + "")));
            }
        } else {
            holder.text.setTextColor(sharedPrefs.getInt("ct_receivedTextColor", context.getResources().getColor(R.color.black)));
            holder.date.setTextColor(convertToColorInt(convertToARGB(sharedPrefs.getInt("ct_receivedTextColor", context.getResources().getColor(R.color.black)), "55")));
            holder.background.setBackgroundColor(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.white)));
            holder.media.setBackgroundColor(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.white)));
            holder.bubble.setColorFilter(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.white)));

            if (!sharedPrefs.getBoolean("custom_theme", false))
            {
                String color = sharedPrefs.getString("received_text_color", "default");

                if (color.equals("blue"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_blue));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_blue));
                } else if (color.equals("white"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.white));
                    holder.date.setTextColor(context.getResources().getColor(R.color.white));
                } else if (color.equals("green"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_green));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_green));
                } else if (color.equals("orange"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_orange));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_orange));
                } else if (color.equals("red"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_red));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_red));
                } else if (color.equals("purple"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.holo_purple));
                    holder.date.setTextColor(context.getResources().getColor(R.color.holo_purple));
                } else if (color.equals("black"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.pitch_black));
                    holder.date.setTextColor(context.getResources().getColor(R.color.pitch_black));
                } else if (color.equals("grey"))
                {
                    holder.text.setTextColor(context.getResources().getColor(R.color.grey));
                    holder.date.setTextColor(context.getResources().getColor(R.color.grey));
                }

                holder.background.setBackgroundColor(convertToColorInt(convertToARGB(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.black)), sharedPrefs.getInt("text_opacity", 100) + "")));
            }
        }

        if (!sharedPrefs.getString("text_alignment", "split").equals("split"))
        {
            if (sharedPrefs.getString("text_alignment", "split").equals("right"))
            {
                holder.text.setGravity(Gravity.RIGHT);
                holder.date.setGravity(Gravity.RIGHT);
            } else
            {
                holder.text.setGravity(Gravity.LEFT);
                holder.date.setGravity(Gravity.LEFT);
            }
        } else if (!sharedPrefs.getBoolean("contact_pictures", true)) {
            if (type == 0) {
                holder.text.setGravity(Gravity.LEFT);
                holder.date.setGravity(Gravity.LEFT);
            } else {
                holder.text.setGravity(Gravity.RIGHT);
                holder.date.setGravity(Gravity.RIGHT);
            }
        }

        if (sharedPrefs.getString("run_as", "sliding").equals("hangout")) {
            int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7, context.getResources().getDisplayMetrics());
            int scale2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, context.getResources().getDisplayMetrics());
            int scale3 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, context.getResources().getDisplayMetrics());
            
            if (type == 1) {
                v.setPadding(scale3, scale2, scale, scale2);
            } else {
                v.setPadding(scale, scale2, scale3, scale2);
            }
        } else if (sharedPrefs.getString("run_as", "sliding").equals("card2")) {
            int scale = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 17, context.getResources().getDisplayMetrics());
            int scale2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());
            v.setPadding(scale, scale2, scale, 0);
        }

        v.setTag(holder);
        return v;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (!mCursor.moveToPosition(getCount() - 1 - position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        View v;
        if (convertView == null) {
            v = newView(context, mCursor, parent);
        } else {
            v = convertView;
        }
        bindView(v, context, mCursor);
        return v;
    }

    static class ViewHolder {
        public TextView text;
        public TextView date;
        public QuickContactBadge image;
        public View background;
        public ImageView media;
        public ImageView ellipsis;
        public Button downloadButton;
        public ImageView bubble;
        public Uri imageUri;
    }

    public void setMessageText(final TextView textView, final String body) {
        if (sharedPrefs.getString("smilies", "with").equals("with"))
        {
            String patternStr = "[^\\x20-\\x7E\\n]";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(body);

            if (matcher.find())
            {
                textView.setText(body);

                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        final Spannable text;

                        if (sharedPrefs.getBoolean("emoji_type", true))
                        {
                            if (sharedPrefs.getBoolean("smiliesType", true)) {
                                text = EmojiConverter2.getSmiledText(context, EmoticonConverter2New.getSmiledText(context, body));
                            } else {
                                text = EmojiConverter2.getSmiledText(context, EmoticonConverter2.getSmiledText(context, body));
                            }
                        } else
                        {
                            if (sharedPrefs.getBoolean("smiliesType", true)) {
                                text = EmojiConverter.getSmiledText(context, EmoticonConverter2New.getSmiledText(context, body));
                            } else {
                                text = EmojiConverter.getSmiledText(context, EmoticonConverter2.getSmiledText(context, body));
                            }
                        }

                        context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                            @Override
                            public void run() {
                                textView.setText(text);
                                Linkify.addLinks(textView, Linkify.ALL);
                            }

                        });
                    }

                }).start();
            } else
            {
                if (sharedPrefs.getBoolean("smiliesType", true)) {
                    textView.setText(EmoticonConverter2New.getSmiledText(context, body));
                } else {
                    textView.setText(EmoticonConverter2.getSmiledText(context, body));
                }

                Linkify.addLinks(textView, Linkify.ALL);
            }
        } else if (sharedPrefs.getString("smilies", "with").equals("without"))
        {
            String patternStr = "[^\\x20-\\x7E\\n]";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(body);

            if (matcher.find())
            {
                textView.setText(body);

                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        final Spannable text;

                        if (sharedPrefs.getBoolean("emoji_type", true))
                        {
                            if (sharedPrefs.getBoolean("smiliesType", true)) {
                                text = EmojiConverter2.getSmiledText(context, EmoticonConverterNew.getSmiledText(context, body));
                            } else {
                                text = EmojiConverter2.getSmiledText(context, EmoticonConverter.getSmiledText(context, body));
                            }
                        } else
                        {
                            if (sharedPrefs.getBoolean("smiliesType", true)) {
                                text = EmojiConverter.getSmiledText(context, EmoticonConverterNew.getSmiledText(context, body));
                            } else {
                                text = EmojiConverter.getSmiledText(context, EmoticonConverter.getSmiledText(context, body));
                            }
                        }

                        context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                            @Override
                            public void run() {
                                textView.setText(text);
                                Linkify.addLinks(textView, Linkify.ALL);
                            }

                        });
                    }

                }).start();
            } else
            {
                if (sharedPrefs.getBoolean("smiliesType", true)) {
                    textView.setText(EmoticonConverterNew.getSmiledText(context, body));
                } else {
                    textView.setText(EmoticonConverter.getSmiledText(context, body));
                }

                Linkify.addLinks(textView, Linkify.ALL);
            }
        } else if (sharedPrefs.getString("smilies", "with").equals("none"))
        {
            String patternStr = "[^\\x20-\\x7E\\n]";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(body);

            if (matcher.find())
            {
                textView.setText(body);

                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        final Spannable text;

                        if (sharedPrefs.getBoolean("emoji_type", true))
                        {
                            text = EmojiConverter2.getSmiledText(context, body);
                        } else
                        {
                            text = EmojiConverter.getSmiledText(context, body);
                        }

                        context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                            @Override
                            public void run() {
                                textView.setText(text);
                                Linkify.addLinks(textView, Linkify.ALL);
                            }

                        });
                    }

                }).start();
            } else
            {
                textView.setText(body);
                Linkify.addLinks(textView, Linkify.ALL);
            }
        } else if (sharedPrefs.getString("smilies", "with").equals("both"))
        {
            String patternStr = "[^\\x20-\\x7E\\n]";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(body);

            if (matcher.find())
            {
                textView.setText(body);

                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        final Spannable text;

                        if (sharedPrefs.getBoolean("emoji_type", true))
                        {
                            if (sharedPrefs.getBoolean("smiliesType", true)) {
                                text = EmojiConverter2.getSmiledText(context, EmoticonConverter3New.getSmiledText(context, body));
                            } else {
                                text = EmojiConverter2.getSmiledText(context, EmoticonConverter3.getSmiledText(context, body));
                            }
                        } else
                        {
                            if (sharedPrefs.getBoolean("smiliesType", true)) {
                                text = EmojiConverter.getSmiledText(context, EmoticonConverter3New.getSmiledText(context, body));
                            } else {
                                text = EmojiConverter.getSmiledText(context, EmoticonConverter3.getSmiledText(context, body));
                            }
                        }

                        context.getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                            @Override
                            public void run() {
                                textView.setText(text);
                                Linkify.addLinks(textView, Linkify.ALL);
                            }

                        });
                    }

                }).start();
            } else
            {
                if (sharedPrefs.getBoolean("smiliesType", true)) {
                    textView.setText(EmoticonConverter3New.getSmiledText(context, body));
                } else {
                    textView.setText(EmoticonConverter3.getSmiledText(context, body));
                }

                Linkify.addLinks(textView, Linkify.ALL);
            }
        }
    }

    public void downloadableMessage(final ViewHolder holder, String id) {
        Cursor locationQuery = context.getContentResolver().query(Uri.parse("content://mms/"), new String[] {"m_size", "exp", "ct_l", "_id"}, "_id=?", new String[]{id}, null);

        if (locationQuery.moveToFirst()) {
            String exp = "1";
            String size = "1";

            try
            {
                size = locationQuery.getString(locationQuery.getColumnIndex("m_size"));
                exp = locationQuery.getString(locationQuery.getColumnIndex("exp"));
            } catch (Exception f)
            {

            }

            String location = locationQuery.getString(locationQuery.getColumnIndex("ct_l"));

            holder.image.setVisibility(View.VISIBLE);
            holder.bubble.setVisibility(View.VISIBLE);
            holder.media.setVisibility(View.GONE);
            holder.text.setText("");
            holder.text.setGravity(Gravity.CENTER);

            holder.text.setTextColor(sharedPrefs.getInt("ct_receivedTextColor", context.getResources().getColor(R.color.black)));
            holder.date.setTextColor(sharedPrefs.getInt("ct_receivedTextColor", context.getResources().getColor(R.color.black)));
            holder.background.setBackgroundColor(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.white)));
            holder.media.setBackgroundColor(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.white)));
            holder.bubble.setColorFilter(sharedPrefs.getInt("ct_receivedMessageBackground", context.getResources().getColor(R.color.white)));
            holder.date.setText("");

            boolean error2 = false;

            try
            {
                holder.date.setText("Message size: " + (int)(Double.parseDouble(size)/1000) + " KB Expires: " +  DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(Long.parseLong(exp) * 1000)));
                holder.downloadButton.setVisibility(View.VISIBLE);
            } catch (Exception f)
            {
                try {
                    holder.date.setText("Error loading message.");
                    holder.downloadButton.setVisibility(View.GONE);
                } catch (Exception g) {
                    error2 = true;
                }
            }

            holder.date.setGravity(Gravity.LEFT);

            final String downloadLocation = location;
            final String msgId = id;

            if (!error2) {
                holder.downloadButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        holder.downloadButton.setVisibility(View.INVISIBLE);

                        if (sharedPrefs.getBoolean("wifi_mms_fix", true))
                        {
                            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                            currentWifi = wifi.getConnectionInfo();
                            currentWifiState = wifi.isWifiEnabled();
                            wifi.disconnect();
                            discon = new DisconnectWifi();
                            context.registerReceiver(discon, new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
                            currentDataState = MainActivity.isMobileDataEnabled(context);
                            MainActivity.setMobileDataEnabled(context, true);
                        }

                        ConnectivityManager mConnMgr =  (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        final int result = mConnMgr.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableMMS");

                        if (result != 0)
                        {
                            IntentFilter filter = new IntentFilter();
                            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                            BroadcastReceiver receiver = new BroadcastReceiver() {

                                @Override
                                public void onReceive(final Context context, Intent intent) {
                                    String action = intent.getAction();

                                    if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
                                    {
                                        return;
                                    }

                                    @SuppressWarnings("deprecation")
                                    NetworkInfo mNetworkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

                                    if ((mNetworkInfo == null) || (mNetworkInfo.getType() != ConnectivityManager.TYPE_MOBILE))
                                    {
                                        return;
                                    }

                                    if (!mNetworkInfo.isConnected())
                                    {
                                        return;
                                    } else
                                    {
                                        new Thread(new Runnable() {

                                            @Override
                                            public void run() {
                                                List<APN> apns = new ArrayList<APN>();

                                                try
                                                {
                                                    APNHelper helper = new APNHelper(context);
                                                    apns = helper.getMMSApns();

                                                } catch (Exception e)
                                                {
                                                    APN apn = new APN(sharedPrefs.getString("mmsc_url", ""), sharedPrefs.getString("mms_port", ""), sharedPrefs.getString("mms_proxy", ""));
                                                    apns.add(apn);

                                                    String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                                                    apns.get(0).MMSCenterUrl = mmscUrl;

                                                    try
                                                    {
                                                        if (sharedPrefs.getBoolean("apn_username_password", false))
                                                        {
                                                            if (!sharedPrefs.getString("apn_username", "").equals("") && !sharedPrefs.getString("apn_username", "").equals(""))
                                                            {
                                                                String mmsc = apns.get(0).MMSCenterUrl;
                                                                String[] parts = mmsc.split("://");
                                                                String newMmsc = parts[0] + "://";

                                                                newMmsc += sharedPrefs.getString("apn_username", "") + ":" + sharedPrefs.getString("apn_password", "") + "@";

                                                                for (int i = 1; i < parts.length; i++)
                                                                {
                                                                    newMmsc += parts[i];
                                                                }

                                                                apns.set(0, new APN(newMmsc, apns.get(0).MMSPort, apns.get(0).MMSProxy));
                                                            }
                                                        }
                                                    } catch (Exception f)
                                                    {
                                                        ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                                            @Override
                                                            public void run() {
                                                                Toast.makeText(context, "There may be an error in your username and password settings.", Toast.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }
                                                }

                                                try {
                                                    byte[] resp = HttpUtils.httpConnection(
                                                            context, SendingProgressTokenManager.NO_TOKEN,
                                                            downloadLocation, null, HttpUtils.HTTP_GET_METHOD,
                                                            !TextUtils.isEmpty(apns.get(0).MMSProxy),
                                                            apns.get(0).MMSProxy,
                                                            Integer.parseInt(apns.get(0).MMSPort));

                                                    boolean groupMMS = false;

                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && sharedPrefs.getBoolean("group_message", false))
                                                    {
                                                        groupMMS = true;
                                                    }

                                                    RetrieveConf retrieveConf = (RetrieveConf) new PduParser(resp).parse();
                                                    PduPersister persister = PduPersister.getPduPersister(context);
                                                    Uri msgUri = persister.persist(retrieveConf, Telephony.Mms.Inbox.CONTENT_URI, true,
                                                            groupMMS, null);

                                                    ContentValues values = new ContentValues(1);
                                                    values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
                                                    SqliteWrapper.update(context, context.getContentResolver(),
                                                            msgUri, values, null, null);
                                                    SqliteWrapper.delete(context, context.getContentResolver(),
                                                            Uri.parse("content://mms/"), "thread_id=? and _id=?", new String[] {threadIds, msgId});

                                                    ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                                        @Override
                                                        public void run() {
                                                            ((MainActivity) context).refreshViewPager3();
                                                        }
                                                    });
                                                } catch (Exception e) {
                                                    e.printStackTrace();

                                                    ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                                        @Override
                                                        public void run() {
                                                            holder.downloadButton.setVisibility(View.VISIBLE);
                                                        }
                                                    });
                                                }

                                                if (sharedPrefs.getBoolean("wifi_mms_fix", true))
                                                {
                                                    try {
                                                        context.unregisterReceiver(discon);
                                                    } catch (Exception e) {

                                                    }

                                                    WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                                                    wifi.setWifiEnabled(false);
                                                    wifi.setWifiEnabled(currentWifiState);
                                                    Log.v("Reconnect", "" + wifi.reconnect());
                                                    MainActivity.setMobileDataEnabled(context, currentDataState);
                                                }

                                            }

                                        }).start();

                                        context.unregisterReceiver(this);
                                    }

                                }

                            };

                            context.registerReceiver(receiver, filter);
                        } else
                        {
                            new Thread(new Runnable() {

                                @Override
                                public void run() {
                                    List<APN> apns = new ArrayList<APN>();

                                    try
                                    {
                                        APNHelper helper = new APNHelper(context);
                                        apns = helper.getMMSApns();

                                    } catch (Exception e)
                                    {
                                        APN apn = new APN(sharedPrefs.getString("mmsc_url", ""), sharedPrefs.getString("mms_port", ""), sharedPrefs.getString("mms_proxy", ""));
                                        apns.add(apn);

                                        String mmscUrl = apns.get(0).MMSCenterUrl != null ? apns.get(0).MMSCenterUrl.trim() : null;
                                        apns.get(0).MMSCenterUrl = mmscUrl;

                                        try
                                        {
                                            if (sharedPrefs.getBoolean("apn_username_password", false))
                                            {
                                                if (!sharedPrefs.getString("apn_username", "").equals("") && !sharedPrefs.getString("apn_username", "").equals(""))
                                                {
                                                    String mmsc = apns.get(0).MMSCenterUrl;
                                                    String[] parts = mmsc.split("://");
                                                    String newMmsc = parts[0] + "://";

                                                    newMmsc += sharedPrefs.getString("apn_username", "") + ":" + sharedPrefs.getString("apn_password", "") + "@";

                                                    for (int i = 1; i < parts.length; i++)
                                                    {
                                                        newMmsc += parts[i];
                                                    }

                                                    apns.set(0, new APN(newMmsc, apns.get(0).MMSPort, apns.get(0).MMSProxy));
                                                }
                                            }
                                        } catch (Exception f)
                                        {
                                            ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                                @Override
                                                public void run() {
                                                    Toast.makeText(context, "There may be an error in your username and password settings.", Toast.LENGTH_LONG).show();
                                                }
                                            });
                                        }
                                    }

                                    try {
                                        byte[] resp = HttpUtils.httpConnection(
                                                context, SendingProgressTokenManager.NO_TOKEN,
                                                downloadLocation, null, HttpUtils.HTTP_GET_METHOD,
                                                !TextUtils.isEmpty(apns.get(0).MMSProxy),
                                                apns.get(0).MMSProxy,
                                                Integer.parseInt(apns.get(0).MMSPort));

                                        boolean groupMMS = false;

                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && sharedPrefs.getBoolean("group_message", false))
                                        {
                                            groupMMS = true;
                                        }

                                        RetrieveConf retrieveConf = (RetrieveConf) new PduParser(resp).parse();
                                        PduPersister persister = PduPersister.getPduPersister(context);
                                        Uri msgUri = persister.persist(retrieveConf, Telephony.Mms.Inbox.CONTENT_URI, true,
                                                groupMMS, null);

                                        ContentValues values = new ContentValues(1);
                                        values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
                                        SqliteWrapper.update(context, context.getContentResolver(),
                                                msgUri, values, null, null);
                                        SqliteWrapper.delete(context, context.getContentResolver(),
                                                Uri.parse("content://mms/"), "thread_id=? and _id=?", new String[] {threadIds, msgId});

                                        ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                            @Override
                                            public void run() {
                                                ((MainActivity) context).refreshViewPager3();
                                            }
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();

                                        ((Activity) context).getWindow().getDecorView().findViewById(android.R.id.content).post(new Runnable() {

                                            @Override
                                            public void run() {
                                                holder.downloadButton.setVisibility(View.VISIBLE);
                                            }
                                        });
                                    }

                                    if (sharedPrefs.getBoolean("wifi_mms_fix", true))
                                    {
                                        try {
                                            context.unregisterReceiver(discon);
                                        } catch (Exception e) {

                                        }

                                        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                                        wifi.setWifiEnabled(false);
                                        wifi.setWifiEnabled(currentWifiState);
                                        Log.v("Reconnect", "" + wifi.reconnect());
                                        MainActivity.setMobileDataEnabled(context, currentDataState);
                                    }

                                }

                            }).start();
                        }

                    }

                });
            }
        }
    }
    
    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }

        return inSampleSize;
    }
    
    public static Bitmap decodeSampledBitmapFromFile(String path,
            int reqWidth, int reqHeight) {

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    public InputStream openDisplayPhoto(long contactId) {
        Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Uri photoUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
        Cursor cursor = context.getContentResolver().query(photoUri,
                new String[] {ContactsContract.Contacts.Photo.PHOTO}, null, null, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                byte[] data = cursor.getBlob(0);
                if (data != null) {
                    return new ByteArrayInputStream(data);
                }
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public Bitmap getFacebookPhoto(String phoneNumber) {
        try
        {
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            Uri photoUri = null;
            ContentResolver cr = context.getContentResolver();
            Cursor contact = cr.query(phoneUri,
                    new String[] { ContactsContract.Contacts._ID }, null, null, null);

            try
            {
                if (contact.moveToFirst()) {
                    long userId = contact.getLong(contact.getColumnIndex(ContactsContract.Contacts._ID));
                    photoUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, userId);
                    contact.close();
                }
                else {
                    Bitmap defaultPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar);

                    if (sharedPrefs.getBoolean("ct_darkContactImage", false))
                    {
                        defaultPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar_dark);
                    }

                    contact.close();
                    return defaultPhoto;
                }
                if (photoUri != null) {
                    InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(
                            cr, photoUri);
                    if (input != null) {
                        contact.close();
                        return BitmapFactory.decodeStream(input);
                    }
                } else {
                    Bitmap defaultPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar);

                    if (sharedPrefs.getBoolean("ct_darkContactImage", false))
                    {
                        defaultPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar_dark);
                    }

                    contact.close();
                    return defaultPhoto;
                }
                Bitmap defaultPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar);

                if (sharedPrefs.getBoolean("ct_darkContactImage", false))
                {
                    defaultPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar_dark);
                }

                contact.close();
                return defaultPhoto;
            } catch (Exception e)
            {
                if (sharedPrefs.getBoolean("ct_darkContactImage", false))
                {
                    contact.close();
                    return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar_dark);
                } else
                {
                    contact.close();
                    return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar);
                }
            }
        } catch (Exception e)
        {
            if (sharedPrefs.getBoolean("ct_darkContactImage", false))
            {
                return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar_dark);
            } else
            {
                return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = context.managedQuery(uri, projection, null, null, null);
        context.startManagingCursor(cursor);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    public Bitmap drawableToBitmap (Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        try
        {
            int width = drawable.getIntrinsicWidth();
            width = width > 0 ? width : 1;
            int height = drawable.getIntrinsicHeight();
            height = height > 0 ? height : 1;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e)
        {
            if (sharedPrefs.getBoolean("ct_darkContactImage", false))
            {
                return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar_dark);
            } else
            {
                return BitmapFactory.decodeResource(context.getResources(), R.drawable.default_avatar);
            }
        }
    }

    private static String getMmsText(String id, Activity context) {
        Uri partURI = Uri.parse("content://mms/part/" + id);
        InputStream is = null;
        StringBuilder sb = new StringBuilder();
        try {
            is = context.getContentResolver().openInputStream(partURI);
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader reader = new BufferedReader(isr);
                String temp = reader.readLine();
                while (temp != null) {
                    sb.append(temp);
                    temp = reader.readLine();
                }
            }
        } catch (IOException e) {}
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return sb.toString();
    }

    private String getAddressNumber(int id) {
        String selectionAdd = new String("msg_id=" + id);
        String uriStr = "content://mms/" + id + "/addr";
        Uri uriAddress = Uri.parse(uriStr);
        Cursor cAdd = context.getContentResolver().query(uriAddress, null,
                selectionAdd, null, null);
        String name = "";
        if (cAdd != null)
        {
            if (cAdd.moveToFirst()) {
                do {
                    String number = cAdd.getString(cAdd.getColumnIndex("address"));
                    if (number != null) {
                        try {
                            Long.parseLong(number.replace("-", ""));
                            name += " " + number;
                        } catch (NumberFormatException nfe) {
                            name += " " + number;
                        }
                    }
                } while (cAdd.moveToNext());
            }

            cAdd.close();
        }

        return name.trim();
    }

    private void saveImage(Bitmap finalBitmap, String d) {

        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/Download");
        myDir.mkdirs();
        String fname = d + ".jpg";
        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://" + Environment.getExternalStorageDirectory())));
        Toast.makeText(context, context.getResources().getString(R.string.save_image), Toast.LENGTH_SHORT).show();
    }

    private Bitmap decodeFile(File f){
        try {
            //Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f),null,o);

            //The new size we want to scale to
            final int REQUIRED_SIZE=200;

            //Find the correct scale value. It should be the power of 2.
            int scale=1;
            while(o.outWidth/scale/2>=REQUIRED_SIZE && o.outHeight/scale/2>=REQUIRED_SIZE)
                scale*=2;

            //Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize=scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {}
        return null;
    }

    public String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    public static Date getZeroTimeDate(Date fecha) {
        Date res = fecha;
        Calendar cal = Calendar.getInstance();

        cal.setTime( fecha );
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        res = (Date) cal.getTime();

        return res;
    }


    Html.ImageGetter imgGetterSent = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            Drawable drawable = null;

            drawable = context.getResources().getDrawable(R.drawable.ic_sent);

            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable
                    .getIntrinsicHeight());

            return drawable;
        }
    };

    Html.ImageGetter imgGetterFail = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            Drawable drawable = null;

            drawable = context.getResources().getDrawable(R.drawable.ic_failed);

            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable
                    .getIntrinsicHeight());

            return drawable;
        }
    };

    public static String convertToARGB(int color, String a) {
        String alpha = a;

        if (alpha.length() > 2) {
            alpha = "FF";
        }

        String red = Integer.toHexString(Color.red(color));
        String green = Integer.toHexString(Color.green(color));
        String blue = Integer.toHexString(Color.blue(color));

        if (alpha.length() == 1) {
            alpha = "0" + alpha;
        }

        if (red.length() == 1) {
            red = "0" + red;
        }

        if (green.length() == 1) {
            green = "0" + green;
        }

        if (blue.length() == 1) {
            blue = "0" + blue;
        }

        return "#" + alpha + red + green + blue;
    }

    public static int convertToColorInt(String argb) throws NumberFormatException {

        if (argb.startsWith("#")) {
            argb = argb.replace("#", "");
        }

        int alpha = -1, red = -1, green = -1, blue = -1;

        if (argb.length() == 8) {
            alpha = Integer.parseInt(argb.substring(0, 2), 16);
            red = Integer.parseInt(argb.substring(2, 4), 16);
            green = Integer.parseInt(argb.substring(4, 6), 16);
            blue = Integer.parseInt(argb.substring(6, 8), 16);
        }
        else if (argb.length() == 6) {
            alpha = 255;
            red = Integer.parseInt(argb.substring(0, 2), 16);
            green = Integer.parseInt(argb.substring(2, 4), 16);
            blue = Integer.parseInt(argb.substring(4, 6), 16);
        }

        return Color.argb(alpha, red, green, blue);
    }
}
