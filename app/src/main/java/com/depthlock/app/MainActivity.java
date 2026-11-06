package com.depthlock.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 100;
    private static final int MAX_IMAGE_SIZE = 2560;

    private Bitmap background;
    private Bitmap rendered;

    private ImageView preview;
    private TextView status;
    private SeekBar sizeBar, xBar, yBar, opacityBar, letterBar;
    private Button colorButton, formatButton, weightButton, fontButton;
    private float dragStartX, dragStartY;
    private int dragStartXProgress, dragStartYProgress;
    private boolean draggingClock = false;

    private int clockColor = Color.WHITE;
    private boolean use24Hour = false;
    private boolean bold = true;
    private int fontIndex = 0;

    private final int[] colors = {
            Color.WHITE,
            Color.BLACK,
            Color.rgb(191, 129, 65),
            Color.rgb(210, 210, 210)
    };
    private final String[] colorNames = {"أبيض", "أسود", "ذهبي", "رمادي"};
    private final Typeface[] fonts = {
            Typeface.create("sans", Typeface.BOLD),
            Typeface.create("sans-serif-condensed", Typeface.BOLD),
            Typeface.create("serif", Typeface.BOLD),
            Typeface.create("monospace", Typeface.BOLD)
    };
    private final String[] fontNames = {"Sans", "Condensed", "Serif", "Mono"};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(2), dp(5), dp(2), dp(5));
        return t;
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private SeekBar seek(int progress, int min, int max) {
        SeekBar b = new SeekBar(this);
        b.setMax(max - min);
        b.setProgress(progress - min);
        b.setTag(min);
        return b;
    }

    private int value(SeekBar b) {
        return (Integer) b.getTag() + b.getProgress();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 17, 26));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(10), dp(14), dp(18));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        page.addView(label("DEPTH LOCK", 24));
        page.addView(label("اضبط الساعة بحرية فوق الصورة — اسحبها بإصبعك مباشرة", 14));

        preview = new ImageView(this);
        preview.setBackgroundColor(Color.rgb(35, 36, 48));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(420));
        pp.setMargins(0, dp(6), 0, dp(6));
        page.addView(preview, pp);
        preview.setOnTouchListener(this::handlePreviewTouch);

        page.addView(button("اختيار صورة", this::pickImage));

        page.addView(label("حجم الساعة", 13));
        sizeBar = seek(55, 10, 100); page.addView(sizeBar);
        page.addView(label("موضع الساعة أفقيًا", 13));
        xBar = seek(50, 0, 100); page.addView(xBar);
        page.addView(label("موضع الساعة رأسيًا", 13));
        yBar = seek(28, 0, 100); page.addView(yBar);
        page.addView(label("شفافية الساعة", 13));
        opacityBar = seek(100, 10, 100); page.addView(opacityBar);
        page.addView(label("تباعد حروف الساعة", 13));
        letterBar = seek(0, -20, 80); page.addView(letterBar);

        colorButton = button("لون الساعة: أبيض", this::cycleColor); page.addView(colorButton);
        formatButton = button("الساعة: 12 ساعة", this::toggleFormat); page.addView(formatButton);
        weightButton = button("الخط: عريض", this::toggleWeight); page.addView(weightButton);
        fontButton = button("نوع الخط: Sans", this::cycleFont); page.addView(fontButton);

        page.addView(button("معاينة التصميم", this::render));
        page.addView(button("حفظ الصورة في المعرض", this::saveImage));

        status = label("اختَر صورة أولًا.", 12);
        page.addView(status);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && background != null) render();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
        sizeBar.setOnSeekBarChangeListener(listener);
        xBar.setOnSeekBarChangeListener(listener);
        yBar.setOnSeekBarChangeListener(listener);
        opacityBar.setOnSeekBarChangeListener(listener);
        letterBar.setOnSeekBarChangeListener(listener);

        setContentView(scroll);
    }

    private boolean handlePreviewTouch(View v, MotionEvent event) {
        if (background == null || rendered == null || xBar == null || yBar == null) return false;
        float[] p = imagePointFromView(event.getX(), event.getY());
        if (p == null) return false;

        ClockBounds bounds = getClockBounds();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (p[0] >= bounds.left && p[0] <= bounds.right && p[1] >= bounds.top && p[1] <= bounds.bottom) {
                draggingClock = true;
                dragStartX = p[0];
                dragStartY = p[1];
                dragStartXProgress = value(xBar);
                dragStartYProgress = value(yBar);
                return true;
            }
            return false;
        }
        if (!draggingClock) return false;

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = p[0] - dragStartX;
            float dy = p[1] - dragStartY;
            float xRange = Math.max(1f, background.getWidth() - bounds.width());
            float yRange = Math.max(1f, background.getHeight() - bounds.height());
            int nx = Math.round(dragStartXProgress + dx / xRange * 100f);
            int ny = Math.round(dragStartYProgress + dy / yRange * 100f);
            xBar.setProgress(clampInt(nx, 0, 100));
            yBar.setProgress(clampInt(ny, 0, 100));
            render();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            draggingClock = false;
            return true;
        }
        return true;
    }

    private int clampInt(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private float[] imagePointFromView(float vx, float vy) {
        if (background == null || preview.getWidth() <= 0 || preview.getHeight() <= 0) return null;
        float iw = background.getWidth(), ih = background.getHeight();
        float vw = preview.getWidth(), vh = preview.getHeight();
        float scale = Math.min(vw / iw, vh / ih);
        float left = (vw - iw * scale) / 2f;
        float top = (vh - ih * scale) / 2f;
        float x = (vx - left) / scale;
        float y = (vy - top) / scale;
        if (x < 0 || y < 0 || x > iw || y > ih) return null;
        return new float[]{x, y};
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Bitmap b = loadScaled(data.getData());
            if (b == null) throw new Exception("decode");
            background = b;
            rendered = null;
            preview.setImageBitmap(background);
            status.setText("تم اختيار الخلفية. اضبط الساعة ثم عاين التصميم.");
            render();
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر فتح الصورة", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap loadScaled(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap source = android.graphics.BitmapFactory.decodeStream(in);
            if (source == null) return null;
            int w = source.getWidth(), h = source.getHeight();
            float scale = Math.min(1f, MAX_IMAGE_SIZE / (float) Math.max(w, h));
            if (scale >= 0.999f) return source;
            Bitmap scaled = Bitmap.createScaledBitmap(source, Math.round(w * scale), Math.round(h * scale), true);
            if (scaled != source) source.recycle();
            return scaled;
        }
    }

    private String clockText() {
        return new SimpleDateFormat(use24Hour ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date());
    }

    private void render() {
        if (background == null) return;
        int w = background.getWidth(), h = background.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Base wallpaper.
        c.drawBitmap(background, 0, 0, p);

        // Clock is deliberately drawn before the foreground subject.
        Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        clock.setColor(Color.argb(value(opacityBar) * 255 / 100,
                Color.red(clockColor), Color.green(clockColor), Color.blue(clockColor)));
        clock.setTypeface(bold ? fonts[fontIndex] : Typeface.create(fonts[fontIndex], Typeface.NORMAL));

        float textSize = Math.max(72f, w * (value(sizeBar) / 100f) * 0.25f);
        clock.setTextSize(textSize);
        String text = clockText();
        float spacing = value(letterBar);
        float textWidth = measureSpacedText(clock, text, spacing);
        Paint.FontMetrics fm = clock.getFontMetrics();
        float textHeight = fm.bottom - fm.top;

        float x;
        float y;
        {
            x = (w - textWidth) * (value(xBar) / 100f);
            y = (h - textHeight) * (value(yBar) / 100f) - fm.top;
        }
        drawSpacedText(c, clock, text, x, y, spacing);

        rendered = out;
        preview.setImageBitmap(rendered);
    }

    private float measureSpacedText(Paint paint, String s, float spacing) {
        float total = 0;
        for (int i = 0; i < s.length(); i++) total += paint.measureText(s, i, i + 1);
        return total + Math.max(0, s.length() - 1) * spacing;
    }

    private void drawSpacedText(Canvas c, Paint paint, String s, float x, float y, float spacing) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            c.drawText(ch, cx, y, paint);
            cx += paint.measureText(ch) + spacing;
        }
    }

    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private void cycleColor() {
        int idx = 0;
        for (int i = 0; i < colors.length; i++) if (clockColor == colors[i]) idx = i;
        idx = (idx + 1) % colors.length;
        clockColor = colors[idx];
        colorButton.setText("لون الساعة: " + colorNames[idx]);
        render();
    }

    private void toggleFormat() {
        use24Hour = !use24Hour;
        formatButton.setText(use24Hour ? "الساعة: 24 ساعة" : "الساعة: 12 ساعة");
        render();
    }

    private void toggleWeight() {
        bold = !bold;
        weightButton.setText(bold ? "الخط: عريض" : "الخط: عادي");
        render();
    }

    private void cycleFont() {
        fontIndex = (fontIndex + 1) % fonts.length;
        fontButton.setText("نوع الخط: " + fontNames[fontIndex]);
        render();
    }

    private void saveImage() {
        if (rendered == null) render();
        if (rendered == null) return;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "DepthLock-" + System.currentTimeMillis() + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DepthLock");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("storage");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                rendered.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            status.setText("تم حفظ الصورة في Pictures/DepthLock.");
        } catch (Exception e) {
            Toast.makeText(this, "فشل حفظ الصورة", Toast.LENGTH_SHORT).show();
        }
    }


    private static class ClockBounds {
        float left, top, right, bottom;
        float width() { return right - left; }
        float height() { return bottom - top; }
    }

    private ClockBounds getClockBounds() {
        ClockBounds b = new ClockBounds();
        if (background == null) return b;
        int w = background.getWidth(), h = background.getHeight();
        Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        clock.setTypeface(bold ? fonts[fontIndex] : Typeface.create(fonts[fontIndex], Typeface.NORMAL));
        float textSize = Math.max(72f, w * (value(sizeBar) / 100f) * 0.25f);
        clock.setTextSize(textSize);
        String text = clockText();
        float spacing = value(letterBar);
        float textWidth = measureSpacedText(clock, text, spacing);
        Paint.FontMetrics fm = clock.getFontMetrics();
        float textHeight = fm.bottom - fm.top;
        b.left = (w - textWidth) * (value(xBar) / 100f);
        b.top = (h - textHeight) * (value(yBar) / 100f);
        b.right = b.left + textWidth;
        b.bottom = b.top + textHeight;
        return b;
    }

}
