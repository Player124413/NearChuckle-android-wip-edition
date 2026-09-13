package com.nearchuckle.farcry;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.TextView;

/**
 * Лаунчер: настраивает путь к игре, графику, сенсор и запускает GameActivity.
 * Также содержит превью редактора сенсорных кнопок (открывает диалог с TouchControlsOverlay).
 */
public class LauncherActivity extends AppCompatActivity {

    private TextView txtFolder, txtResValue;
    private Slider sliderFps, sliderRes, sliderSens, sliderOpacity;
    private SwitchMaterial switchTouch, switchDynRes;
    private MaterialButton btnChooseFolder, btnVerify, btnPlay, btnEditTouch;
    private MaterialButton btnAngle, btnGles, btnAuto;
    private SettingsManager settings;

    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        settings = SettingsManager.get(this);

        txtFolder = findViewById(R.id.txtFolder);
        txtResValue = findViewById(R.id.txtResValue);
        sliderFps = findViewById(R.id.sliderFps);
        sliderRes = findViewById(R.id.sliderRes);
        sliderSens = findViewById(R.id.sliderSens);
        sliderOpacity = findViewById(R.id.sliderOpacity);
        switchTouch = findViewById(R.id.switchTouch);
        switchDynRes = findViewById(R.id.switchDynRes);
        btnChooseFolder = findViewById(R.id.btnChooseFolder);
        btnVerify = findViewById(R.id.btnVerify);
        btnPlay = findViewById(R.id.btnPlay);
        btnEditTouch = findViewById(R.id.btnEditTouch);
        btnAngle = findViewById(R.id.btnAngle);
        btnGles = findViewById(R.id.btnGles);
        btnAuto = findViewById(R.id.btnAuto);

        folderPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData()!=null) {
                Uri uri = result.getData().getData();
                if (uri!=null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    // Try to resolve real path via DocumentFile
                    String path = uriToPath(uri);
                    if (path!=null) {
                        settings.setGameFolder(path);
                    } else {
                        // fallback: store uri string
                        settings.setGameFolder(uri.toString());
                    }
                    refreshFolder();
                }
            }
        });

        refreshFolder();
        loadSettingsToUI();
        setupListeners();
    }

    private void refreshFolder() {
        String folder = settings.getGameFolder();
        txtFolder.setText(folder);
    }

    private String uriToPath(Uri treeUri) {
        try {
            // For SAF tree, try to get display path; on many devices we just use /storage/emulated/0/...
            // We'll attempt to map to file path via heuristic: decode last segment
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            // docId like "primary:FarCry" or "1234-5678:FarCry"
            if (docId.contains(":")) {
                String[] parts = docId.split(":");
                String volume = parts[0];
                String rel = parts.length>1? parts[1] : "";
                if ("primary".equals(volume)) return "/storage/emulated/0/" + rel;
                else return "/storage/" + volume + "/" + rel;
            }
            return null;
        } catch (Exception e) { return null; }
    }

    private void loadSettingsToUI() {
        String rend = settings.getRenderer();
        btnAngle.setChecked("angle".equals(rend));
        btnGles.setChecked("gles".equals(rend));
        btnAuto.setChecked("auto".equals(rend) || rend==null);

        sliderFps.setValue(settings.getFpsLimit());
        sliderRes.setValue(settings.getResScale());
        updateResLabel(settings.getResScale());
        switchDynRes.setChecked(settings.isDynRes());
        switchTouch.setChecked(settings.isTouchEnabled());
        sliderSens.setValue(settings.getTouchSens());
        sliderOpacity.setValue(settings.getTouchOpacity());
    }

    private void updateResLabel(float scale) {
        String txt;
        if (scale >= 0.95f) txt = String.format("%.0f%% • Макс качество", scale*100);
        else if (scale >= 0.75f) txt = String.format("%.0f%% • Баланс", scale*100);
        else if (scale >= 0.6f) txt = String.format("%.0f%% • Производительность", scale*100);
        else txt = String.format("%.0f%% • Макс FPS (мыльно)", scale*100);
        txtResValue.setText(txt);
    }

    private void setupListeners() {
        btnChooseFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { folderPickerLauncher.launch(intent); }
            catch (Exception e) { Toast.makeText(this, "SAF не доступен, введи путь вручную", Toast.LENGTH_LONG).show(); showManualPathDialog(); }
        });

        txtFolder.setOnClickListener(v -> showManualPathDialog());

        btnVerify.setOnClickListener(v -> verifyGameFolder());

        btnAngle.setOnClickListener(v -> { settings.setRenderer("angle"); loadSettingsToUI(); Toast.makeText(this,"ANGLE: GLES→Vulkan via Google ANGLE — стабильно на Mali/Adreno",Toast.LENGTH_SHORT).show(); });
        btnGles.setOnClickListener(v -> { settings.setRenderer("gles"); loadSettingsToUI(); });
        btnAuto.setOnClickListener(v -> { settings.setRenderer("auto"); loadSettingsToUI(); });

        sliderFps.addOnChangeListener((s,val,fromUser)-> { if(fromUser) settings.setFpsLimit((int)val); });
        sliderRes.addOnChangeListener((s,val,fromUser)-> { if(fromUser){ settings.setResScale(val); updateResLabel(val);} });
        sliderSens.addOnChangeListener((s,val,fromUser)-> { if(fromUser) settings.setTouchSens(val); });
        sliderOpacity.addOnChangeListener((s,val,fromUser)-> { if(fromUser) settings.setTouchOpacity(val); });
        switchDynRes.setOnCheckedChangeListener((b,chk)-> settings.setDynRes(chk));
        switchTouch.setOnCheckedChangeListener((b,chk)-> settings.setTouchEnabled(chk));

        btnEditTouch.setOnClickListener(v -> showTouchEditorDialog());
        btnPlay.setOnClickListener(v -> launchGame());
    }

    private void showManualPathDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(settings.getGameFolder());
        input.setHint("/storage/emulated/0/FarCry");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Путь к Far Cry")
                .setMessage("Укажи папку где лежит FCData, Levels, Shaders. Можно из SAF выше или вручную.")
                .setView(input)
                .setPositiveButton("Сохранить", (d,w)-> { settings.setGameFolder(input.getText().toString().trim()); refreshFolder(); })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void verifyGameFolder() {
        String folder = settings.getGameFolder();
        // Quick heuristic checks
        boolean looksLikeUri = folder.startsWith("content://");
        String summary;
        if (looksLikeUri) {
            try {
                Uri uri = Uri.parse(folder);
                DocumentFile df = DocumentFile.fromTreeUri(this, uri);
                boolean hasFCData = false, hasLevels=false;
                if (df!=null && df.exists()) {
                    for (DocumentFile f: df.listFiles()) {
                        if ("FCData".equalsIgnoreCase(f.getName())) hasFCData=true;
                        if ("Levels".equalsIgnoreCase(f.getName())) hasLevels=true;
                    }
                }
                if (hasFCData && hasLevels) summary = getString(R.string.msg_verify_ok) + "\n" + df.getName();
                else summary = getString(R.string.msg_verify_fail) + "\nНайдено в SAF: FCData=" + hasFCData + " Levels=" + hasLevels;
                new MaterialAlertDialogBuilder(this).setTitle("Проверка").setMessage(summary).setPositiveButton("OK",null).show();
                return;
            } catch (Exception e) { Toast.makeText(this,"Ошибка SAF: "+e.getMessage(),Toast.LENGTH_LONG).show(); return; }
        }
        java.io.File f = new java.io.File(folder);
        java.io.File fc = new java.io.File(f, "FCData");
        java.io.File lv = new java.io.File(f, "Levels");
        boolean ok = fc.exists() && lv.exists();
        String msg = ok? getString(R.string.msg_verify_ok) : getString(R.string.msg_verify_fail) + "\nПуть: "+folder+"\nFCData exists="+fc.exists()+" Levels exists="+lv.exists();
        new MaterialAlertDialogBuilder(this).setTitle("Проверка файлов").setMessage(msg).setPositiveButton("OK",null).show();
    }

    private void showTouchEditorDialog() {
        // Создаём полноэкранный диалог с TouchControlsOverlay прямо в лаунчере для превью и настройки
        android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(0xFF0F1419);

        // Fake game preview background
        android.widget.ImageView bg = new android.widget.ImageView(this);
        bg.setBackgroundColor(0xFF1E2E3A);
        bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        // Could load screenshot; use color for now
        root.addView(bg, new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(this);
        hint.setText("Превью управления • Перетаскивай, щипок = размер, 👁 = видимость");
        hint.setTextColor(0xFFFFFFFF);
        hint.setBackgroundColor(0x99000000);
        hint.setPadding(24,16,24,16);
        hint.setTextSize(11);
        android.widget.FrameLayout.LayoutParams hp = new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(hint, hp);

        TouchControlsOverlay overlay = new TouchControlsOverlay(this);
        overlay.setEditMode(true);
        root.addView(overlay, new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        // Bottom bar
        android.widget.LinearLayout bar = new android.widget.LinearLayout(this);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF121A21);
        bar.setPadding(12,12,12,12);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        MaterialButton btnSave = new MaterialButton(this);
        btnSave.setText("Сохранить");
        btnSave.setIconResource(android.R.drawable.ic_menu_save);
        MaterialButton btnReset = new MaterialButton(this);
        btnReset.setText("Сброс");
        MaterialButton btnToggle = new MaterialButton(this);
        btnToggle.setText(settings.isTouchEnabled()? "Отключить сенсор":"Включить сенсор");
        MaterialButton btnClose = new MaterialButton(this);
        btnClose.setText("Закрыть");

        bar.addView(btnSave, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(btnReset, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(btnToggle, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(btnClose, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        // margins
        for (int i=0;i<bar.getChildCount();i++) { android.view.View ch=bar.getChildAt(i); android.widget.LinearLayout.LayoutParams lp=(android.widget.LinearLayout.LayoutParams)ch.getLayoutParams(); lp.setMargins(6,0,6,0); ch.setLayoutParams(lp); }

        android.widget.FrameLayout.LayoutParams barLp = new android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = android.view.Gravity.BOTTOM;
        root.addView(bar, barLp);

        dialog.setContentView(root);
        dialog.show();

        btnSave.setOnClickListener(v-> { overlay.saveLayout(this); Toast.makeText(this,"Сохранено",Toast.LENGTH_SHORT).show(); dialog.dismiss(); });
        btnReset.setOnClickListener(v-> { overlay.resetLayout(this); });
        btnToggle.setOnClickListener(v-> { overlay.toggleTouchEnabled(); btnToggle.setText(overlay.isTouchEnabled()? "Отключить сенсор":"Включить сенсор"); });
        btnClose.setOnClickListener(v-> dialog.dismiss());
        overlay.setOnEditModeListener(new TouchControlsOverlay.OnEditModeListener() {
            public void onEditModeChanged(boolean e) {}
            public void onTouchEnabledChanged(boolean e) { btnToggle.setText(e? "Отключить сенсор":"Включить сенсор"); }
            public void onRequestSave() {}
        });
        // opacity/sens live update? not needed here
    }

    private void launchGame() {
        String folder = settings.getGameFolder();
        if (folder==null || folder.isEmpty()) { Toast.makeText(this, R.string.msg_no_game_folder, Toast.LENGTH_LONG).show(); return; }
        // Minimal check, but allow launch anyway with warning
        if (!looksValidFolder(folder)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Папка не выглядит как Far Cry")
                    .setMessage("FCData/Levels не найдены. Запустить всё равно? Игра может вылететь.")
                    .setPositiveButton("Запустить", (d,w)-> startGameActivity())
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            startGameActivity();
        }
    }

    private boolean looksValidFolder(String folder) {
        try {
            if (folder.startsWith("content://")) return true; // SAF assume ok after verify
            java.io.File f = new java.io.File(folder);
            return new java.io.File(f,"FCData").exists();
        } catch (Exception e){ return false; }
    }

    private void startGameActivity() {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("game_folder", settings.getGameFolder());
        i.putExtra("renderer", settings.getRenderer());
        i.putExtra("fps_limit", settings.getFpsLimit());
        i.putExtra("res_scale", settings.getResScale());
        i.putExtra("dyn_res", settings.isDynRes());
        i.putExtra("touch_enabled", settings.isTouchEnabled());
        i.putExtra("touch_sens", settings.getTouchSens());
        startActivity(i);
    }
}
