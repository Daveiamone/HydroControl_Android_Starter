
package ru.hydrocontrol;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private DbHelper db;
    private EditText phInput;
    private EditText ecInput;
    private EditText ppmInput;
    private Spinner stageSpinner;
    private TextView adviceText;
    private TextView historyText;
    private ChartView chartView;

    private final String[] stages = {
        "После пересадки / укоренение",
        "Рост листьев",
        "Цветение",
        "Плодоношение"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = new DbHelper(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(0xFFF4F7F4);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = createText(
            "HydroControl", 28, 0xFF245B35, true
        );
        root.addView(title);

        root.addView(createText(
            "Гидропоника · клубника «Альбион»",
            16, 0xFF3E5142, false
        ));

        root.addView(createText(
            "Запись измерений без интернета",
            14, 0xFF647066, false
        ));

        addLabel(root, "Стадия развития растения");

        stageSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            stages
        );
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        stageSpinner.setAdapter(adapter);

        LinearLayout.LayoutParams spinnerParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        spinnerParams.bottomMargin = dp(12);
        root.addView(stageSpinner, spinnerParams);

        phInput = addInput(
            root, "pH раствора", "Например, 5.8",
            InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED
        );

        ecInput = addInput(
            root, "EC (мСм/см)", "Например, 1.4",
            InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED
        );

        ppmInput = addInput(
            root, "PPM", "Например, 900",
            InputType.TYPE_CLASS_NUMBER
        );

        Button saveButton = new Button(this);
        saveButton.setText("Сохранить измерение");
        saveButton.setOnClickListener(v -> saveMeasurement());
        root.addView(saveButton);

        addSection(root, "Рекомендации");

        adviceText = createText(
            "Введи показатели раствора и сохрани измерение.",
            15, 0xFF26372A, false
        );
        adviceText.setPadding(
            dp(12), dp(12), dp(12), dp(12)
        );
        adviceText.setBackgroundColor(0xFFE6EEE6);
        root.addView(adviceText);

        addSection(root, "График pH");

        chartView = new ChartView(this);
        root.addView(
            chartView,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(235)
            )
        );

        addSection(root, "История измерений");

        historyText = createText(
            "Пока нет сохранённых измерений.",
            14, 0xFF26372A, false
        );
        root.addView(historyText);

        root.addView(createText(
            "PPM зависит от шкалы конкретного прибора. "
                + "Используй его для сравнения показаний "
                + "одного и того же измерителя.",
            13, 0xFF647066, false
        ));

        refreshHistory();
    }

    private int dp(float value) {
        return (int) (
            value * getResources().getDisplayMetrics().density
                + 0.5f
        );
    }

    private TextView createText(
        String value, float size, int color, boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        text.setPadding(0, 0, 0, dp(7));
        return text;
    }

    private void addLabel(LinearLayout parent, String value) {
        parent.addView(
            createText(value, 15, 0xFF26372A, true)
        );
    }

    private void addSection(LinearLayout parent, String value) {
        TextView text = createText(
            value, 20, 0xFF245B35, true
        );
        text.setPadding(0, dp(20), 0, dp(10));
        parent.addView(text);
    }

    private EditText addInput(
        LinearLayout parent,
        String label,
        String hint,
        int inputType
    ) {
        addLabel(parent, label);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setPadding(
            dp(12), dp(10), dp(12), dp(10)
        );

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        params.bottomMargin = dp(12);
        parent.addView(input, params);

        return input;
    }

    private void saveMeasurement() {
        try {
            double ph = Double.parseDouble(
                phInput.getText().toString()
                    .trim().replace(',', '.')
            );

            double ec = Double.parseDouble(
                ecInput.getText().toString()
                    .trim().replace(',', '.')
            );

            int ppm = Integer.parseInt(
                ppmInput.getText().toString().trim()
            );

            if (ph < 0 || ph > 14 || ec < 0 || ppm < 0) {
                Toast.makeText(
                    this,
                    "Проверь введённые значения.",
                    Toast.LENGTH_LONG
                ).show();
                return;
            }

            int stageIndex = stageSpinner.getSelectedItemPosition();
            String stage = stages[stageIndex];

            ContentValues values = new ContentValues();
            values.put("measured_at", System.currentTimeMillis());
            values.put("stage", stage);
            values.put("ph", ph);
            values.put("ec", ec);
            values.put("ppm", ppm);

            long result = db.getWritableDatabase().insert(
                "measurements", null, values
            );

            if (result == -1) {
                Toast.makeText(
                    this,
                    "Не удалось сохранить запись.",
                    Toast.LENGTH_LONG
                ).show();
                return;
            }

            adviceText.setText(
                makeAdvice(ph, ec, stageIndex)
            );

            phInput.setText("");
            ecInput.setText("");
            ppmInput.setText("");

            refreshHistory();

            Toast.makeText(
                this,
                "Измерение сохранено.",
                Toast.LENGTH_SHORT
            ).show();

        } catch (NumberFormatException e) {
            Toast.makeText(
                this,
                "Введи pH, EC и PPM числами.",
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private String makeAdvice(
        double ph, double ec, int stageIndex
    ) {
        StringBuilder out = new StringBuilder();

        out.append("Клубника «Альбион»\n\n");

        if (ph < 5.3) {
            out.append(
                "pH заметно ниже ориентировочного "
                + "диапазона 5.5–6.2. Проверь калибровку "
                + "измерителя и состояние раствора.\n\n"
            );
        } else if (ph < 5.5) {
            out.append(
                "pH немного ниже ориентира 5.5–6.2. "
                + "Наблюдай за динамикой и точностью прибора.\n\n"
            );
        } else if (ph > 6.3) {
            out.append(
                "pH выше ориентира 5.5–6.2. "
                + "Проверь измеритель и раствор; "
                + "не корректируй pH резко.\n\n"
            );
        } else if (ph > 6.2) {
            out.append(
                "pH немного выше ориентира 5.5–6.2. "
                + "Следи за изменениями показаний.\n\n"
            );
        } else {
            out.append(
                "pH в ориентировочном диапазоне 5.5–6.2.\n\n"
            );
        }

        double[] ecMin = {0.6, 1.0, 1.2, 1.3};
        double[] ecMax = {1.2, 1.5, 1.7, 1.8};

        out.append(
            "EC для выбранной стадии: примерно "
        );
        out.append(String.format(
            Locale.US, "%.1f–%.1f мСм/см.\n",
            ecMin[stageIndex], ecMax[stageIndex]
        ));

        if (ec < ecMin[stageIndex]) {
            out.append(
                "Измеренный EC ниже этого ориентира. "
                + "Проверь питание и состояние растений; "
                + "не увеличивай концентрацию резко.\n\n"
            );
        } else if (ec > ecMax[stageIndex]) {
            out.append(
                "Измеренный EC выше этого ориентира. "
                + "Проверь уровень воды, испарение, "
                + "температуру и точность измерителя. "
                + "Не разбавляй раствор вслепую.\n\n"
            );
        } else {
            out.append(
                "EC находится в заданном ориентировочном "
                + "диапазоне для выбранной стадии.\n\n"
            );
        }

        out.append(
            "Важно: значения EC — предварительные "
            + "ориентиры, а не универсальная норма. "
            + "Учитывай состав удобрения, температуру, "
            + "реакцию растений и рекомендации производителя. "
            + "PPM сохраняется для истории, но не оценивается "
            + "по универсальному порогу."
        );

        return out.toString();
    }

    private void refreshHistory() {
        ArrayList<Sample> samples = new ArrayList<>();

        Cursor cursor = db.getReadableDatabase().rawQuery(
            "SELECT measured_at, stage, ph, ec, ppm "
                + "FROM measurements "
                + "ORDER BY measured_at DESC LIMIT 10",
            null
        );

        try {
            while (cursor.moveToNext()) {
                samples.add(new Sample(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getDouble(2),
                    cursor.getDouble(3),
                    cursor.getInt(4)
                ));
            }
        } finally {
            cursor.close();
        }

        Collections.reverse(samples);

        if (samples.isEmpty()) {
            historyText.setText(
                "Пока нет сохранённых измерений."
            );
        } else {
            StringBuilder history = new StringBuilder();
            SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                    "dd.MM.yyyy HH:mm", Locale.getDefault()
                );

            for (Sample sample : samples) {
                history.append(
                    dateFormat.format(new Date(sample.time))
                );
                history.append("\n");
                history.append(sample.stage);
                history.append("\n");
                history.append(String.format(
                    Locale.getDefault(),
                    "pH: %.2f | EC: %.2f | PPM: %d",
                    sample.ph, sample.ec, sample.ppm
                ));
                history.append("\n\n");
            }

            historyText.setText(history.toString());
        }

        chartView.setSamples(samples);
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }
        super.onDestroy();
    }

    private static class Sample {
        final long time;
        final String stage;
        final double ph;
        final double ec;
        final int ppm;

        Sample(
            long time, String stage,
            double ph, double ec, int ppm
        ) {
            this.time = time;
            this.stage = stage;
            this.ph = ph;
            this.ec = ec;
            this.ppm = ppm;
        }
    }

    private static class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, "hydrocontrol.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE measurements ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "measured_at INTEGER NOT NULL, "
                    + "stage TEXT NOT NULL, "
                    + "ph REAL NOT NULL, "
                    + "ec REAL NOT NULL, "
                    + "ppm INTEGER NOT NULL"
                    + ")"
            );
        }

        @Override
        public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
        ) {
            // Схема базы пока не меняется.
        }
    }

    private class ChartView extends View {

        private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

        private List<Sample> samples = new ArrayList<>();

        ChartView(Context context) {
            super(context);
            setBackgroundColor(Color.WHITE);
        }

        void setSamples(List<Sample> values) {
            samples = new ArrayList<>(values);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.WHITE);

            float d = getResources()
                .getDisplayMetrics().density;

            float left = 42 * d;
            float right = getWidth() - 12 * d;
            float top = 34 * d;
            float bottom = getHeight() - 28 * d;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF26372A);
            paint.setTextSize(13 * d);
            canvas.drawText(
                "Последние измерения pH", left, 18 * d, paint
            );

            if (samples.isEmpty()) {
                paint.setTextSize(12 * d);
                canvas.drawText(
                    "График появится после первого измерения.",
                    left, top + 25 * d, paint
                );
                return;
            }

            float yTargetHigh = chartY(6.2, top, bottom);
            float yTargetLow = chartY(5.5, top, bottom);

            paint.setColor(0xFFE8F2E8);
            canvas.drawRect(
                left, yTargetHigh, right, yTargetLow, paint
            );

            paint.setColor(0xFFDCE3DC);
            paint.setStrokeWidth(d);
            paint.setStyle(Paint.Style.STROKE);

            for (double level = 4.5; level <= 7.01; level += 0.5) {
                float y = chartY(level, top, bottom);
                canvas.drawLine(left, y, right, y, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(10 * d);
                paint.setColor(0xFF647066);
                canvas.drawText(
                    String.format(Locale.US, "%.1f", level),
                    3 * d, y + 4 * d, paint
                );

                paint.setColor(0xFFDCE3DC);
                paint.setStyle(Paint.Style.STROKE);
            }

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * d);
            paint.setColor(0xFF39834B);

            float previousX = 0;
            float previousY = 0;
            boolean hasPrevious = false;

            for (int i = 0; i < samples.size(); i++) {
                Sample sample = samples.get(i);

                float x = left + (right - left)
                    * (i / (float) Math.max(
                        1, samples.size() - 1
                    ));

                float y = chartY(sample.ph, top, bottom);

                if (hasPrevious) {
                    canvas.drawLine(
                        previousX, previousY, x, y, paint
                    );
                }

                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, 4 * d, paint);
                paint.setStyle(Paint.Style.STROKE);

                previousX = x;
                previousY = y;
                hasPrevious = true;
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(10 * d);
            paint.setColor(0xFF647066);
            canvas.drawText("Старые", left, getHeight() - 5 * d, paint);

            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(
                "Новые", right, getHeight() - 5 * d, paint
            );
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private float chartY(
            double value, float top, float bottom
        ) {
            double bounded = Math.max(
                4.5, Math.min(7.0, value)
            );

            return bottom - (float)(
                (bounded - 4.5) / (7.0 - 4.5)
            ) * (bottom - top);
        }
    }
}
