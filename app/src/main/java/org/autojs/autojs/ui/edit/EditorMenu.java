package org.autojs.autojs.ui.edit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import io.reactivex.android.schedulers.AndroidSchedulers;
import org.autojs.autojs.core.pref.Language;
import org.autojs.autojs.core.pref.Pref;
import org.autojs.autojs.extension.MaterialDialogExtensions;
import org.autojs.autojs.model.indices.AndroidClass;
import org.autojs.autojs.model.indices.ClassSearchingItem;
import org.autojs.autojs.script.JavaScriptFileSource;
import org.autojs.autojs.ui.common.NotAskAgainDialog;
import org.autojs.autojs.ui.edit.editor.CodeEditor;
import org.autojs.autojs.ui.main.scripts.EditableFileInfoDialogManager;
import org.autojs.autojs.ui.project.BuildActivity;
import org.autojs.autojs.util.ClipboardUtils;
import org.autojs.autojs.util.ConsoleUtils;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;
import org.autojs.autojs.agent.core.AgentService;
import org.autojs.autojs.agent.ui.ChatInterface;
import kotlinx.coroutines.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.autojs.autojs.util.StringUtils.key;

/**
 * Created by Stardust on Sep 28, 2017.
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
@SuppressLint("CheckResult")
public class EditorMenu {

    private final EditorView mEditorView;
    private final Context mContext;
    private final CodeEditor mEditor;
    private AgentService mAgentService;
    private ChatInterface mChatInterface;

    public EditorMenu(EditorView editorView) {
        mEditorView = editorView;
        mContext = editorView.getContext();
        mEditor = editorView.editor;
        initializeAgentService();
    }

    private void initializeAgentService() {
        try {
            mAgentService = AgentService.getInstance(mContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_log) {
            return ConsoleUtils.launch(mContext);
        }
        if (itemId == R.id.action_force_stop) {
            return tryDoing(mEditorView::forceStop);
        }
        return onAgentOptionsSelected(item)
               || onEditOptionsSelected(item)
               || onJumpOptionsSelected(item)
               || onMoreOptionsSelected(item)
               || onDebugOptionsSelected(item);
    }

    private boolean onAgentOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.action_toggle_agent_panel) {
            mEditorView.toggleAgentPanel();
            return true;
        }
        
        if (itemId == R.id.action_ai_chat) {
            showAIChat();
            return true;
        }
        
        if (itemId == R.id.action_optimize_script) {
            optimizeScriptWithAI();
            return true;
        }
        
        if (itemId == R.id.action_generate_script) {
            generateScriptWithAI();
            return true;
        }
        
        if (itemId == R.id.action_script_templates) {
            showScriptTemplates();
            return true;
        }
        
        if (itemId == R.id.action_agent_settings) {
            showAgentSettings();
            return true;
        }
        
        return false;
    }

    private void showAIChat() {
        if (mChatInterface == null) {
            mChatInterface = new ChatInterface(mContext);
        }
        mChatInterface.show();
    }

    private void optimizeScriptWithAI() {
        String currentScript = mEditor.getText();
        if (TextUtils.isEmpty(currentScript)) {
            ViewUtils.showToast(mContext, "没有可优化的脚本内容");
            return;
        }

        MaterialDialog progressDialog = new MaterialDialog.Builder(mContext)
                .title("AI优化脚本")
                .content("正在分析和优化脚本，请稍候...")
                .progress(true, 0)
                .cancelable(false)
                .show();

        Job optimizationJob = CoroutineKt.runBlocking(GlobalScope.INSTANCE, (scope, continuation) -> {
            try {
                if (mAgentService == null) {
                    throw new Exception("Agent服务未初始化");
                }
                
                var result = mAgentService.analyzeAndOptimizeScript(currentScript);
                
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (result.isSuccessful()) {
                        showOptimizationResult(result);
                    } else {
                        ViewUtils.showToast(mContext, 
                            "脚本优化失败: " + (result.getWarnings().isEmpty() ? "未知错误" : result.getWarnings().get(0)));
                    }
                });
                
            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    ViewUtils.showToast(mContext, "优化失败: " + e.getMessage());
                });
            }
            return null;
        });
    }

    private void showOptimizationResult(org.autojs.autojs.agent.model.OptimizationResult result) {
        String improvements = result.getImprovements().isEmpty() ? 
            "未发现明显需要改进的地方" : 
            String.join("\n", result.getImprovements().stream()
                .map(imp -> "• " + imp.getDescription())
                .toArray(String[]::new));

        new MaterialDialog.Builder(mContext)
            .title("脚本优化结果 (评分: " + (int)result.getScore() + "/100)")
            .content("改进建议:\n" + improvements)
            .positiveText("应用优化")
            .negativeText("取消")
            .neutralText("查看对比")
            .onPositive((dialog, which) -> {
                mEditor.setText(result.getOptimizedScript());
                ViewUtils.showSnack(mEditorView, "脚本已优化");
            })
            .onNeutral((dialog, which) -> {
                showScriptComparison(result.getOriginalScript(), result.getOptimizedScript());
            })
            .show();
    }

    private void showScriptComparison(String original, String optimized) {
        new MaterialDialog.Builder(mContext)
            .title("脚本对比")
            .content("原始脚本:\n" + original + "\n\n优化后脚本:\n" + optimized)
            .positiveText("应用优化")
            .negativeText("保持原样")
            .onPositive((dialog, which) -> {
                mEditor.setText(optimized);
                ViewUtils.showSnack(mEditorView, "脚本已优化");
            })
            .show();
    }

    private void generateScriptWithAI() {
        new MaterialDialog.Builder(mContext)
            .title("AI生成脚本")
            .content("请描述您想要实现的功能:")
            .input("例如: 自动点击屏幕上的确定按钮", "", (dialog, input) -> {
                if (!TextUtils.isEmpty(input)) {
                    performScriptGeneration(input.toString());
                }
            })
            .positiveText("生成")
            .negativeText("取消")
            .show();
    }

    private void performScriptGeneration(String request) {
        MaterialDialog progressDialog = new MaterialDialog.Builder(mContext)
                .title("AI生成脚本")
                .content("正在根据您的需求生成脚本，请稍候...")
                .progress(true, 0)
                .cancelable(false)
                .show();

        Job generationJob = CoroutineKt.runBlocking(GlobalScope.INSTANCE, (scope, continuation) -> {
            try {
                if (mAgentService == null) {
                    throw new Exception("Agent服务未初始化");
                }
                
                var result = mAgentService.generateScript(request);
                
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    
                    if (result.isExecutable()) {
                        showGeneratedScript(result);
                    } else {
                        ViewUtils.showToast(mContext, "脚本生成失败: " + result.getExplanation());
                    }
                });
                
            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    progressDialog.dismiss();
                    ViewUtils.showToast(mContext, "生成失败: " + e.getMessage());
                });
            }
            return null;
        });
    }

    private void showGeneratedScript(org.autojs.autojs.agent.model.ScriptGenerationResult result) {
        new MaterialDialog.Builder(mContext)
            .title("脚本生成完成 (置信度: " + (int)(result.getConfidence() * 100) + "%)")
            .content("生成的脚本:\n\n" + result.getScript() + "\n\n说明: " + result.getExplanation())
            .positiveText("插入到编辑器")
            .negativeText("取消")
            .neutralText("替换当前内容")
            .onPositive((dialog, which) -> {
                mEditor.insert(result.getScript());
                ViewUtils.showSnack(mEditorView, "脚本已插入");
            })
            .onNeutral((dialog, which) -> {
                mEditor.setText(result.getScript());
                ViewUtils.showSnack(mEditorView, "脚本已替换");
            })
            .show();
    }

    private void showScriptTemplates() {
        new MaterialDialog.Builder(mContext)
            .title("脚本模板库")
            .content("模板库功能开发中，敬请期待...")
            .positiveText("确定")
            .show();
    }

    private void showAgentSettings() {
        new MaterialDialog.Builder(mContext)
            .title("AI Agent 设置")
            .content("Agent设置功能开发中，敬请期待...")
            .positiveText("确定")
            .show();
    }

    private boolean onDebugOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_breakpoint) {
            mEditor.addOrRemoveBreakpointAtCurrentLine();
            return true;
        }
        if (itemId == R.id.action_launch_debugger) {
            var builder = new NotAskAgainDialog.Builder(mEditorView.getContext(), "editor.debug.long_click_hint")
                    .title(R.string.text_prompt)
                    .content(R.string.hint_long_click_run_to_debug)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default);
            MaterialDialogExtensions.widgetThemeColor(builder);
            builder.show();
            return tryDoing(mEditorView::debug);
        }
        if (itemId == R.id.action_remove_all_breakpoints) {
            mEditor.removeAllBreakpoints();
            return true;
        }
        return false;
    }

    private boolean onEditOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_find_or_replace) {
            findOrReplace();
            return true;
        }
        if (itemId == R.id.action_copy_all) {
            copyAll();
            return true;
        }
        if (itemId == R.id.action_copy_line) {
            return copyLine();
        }
        if (itemId == R.id.action_delete_line) {
            return deleteLine();
        }
        if (itemId == R.id.action_paste) {
            return paste();
        }
        if (itemId == R.id.action_clear) {
            return tryDoing(() -> mEditor.setText(""));
        }
        if (itemId == R.id.action_comment) {
            return tryDoing(mEditor.commentHelper::handle);
        }
        if (itemId == R.id.action_beautify) {
            return tryDoing(mEditorView::beautifyCode);
        }
        return false;
    }

    private boolean onJumpOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_jump_to_line) {
            jumpToLine();
            return true;
        }
        if (itemId == R.id.action_jump_to_start) {
            mEditor.jumpToStart();
            return true;
        }
        if (itemId == R.id.action_jump_to_end) {
            mEditor.jumpToEnd();
            return true;
        }
        if (itemId == R.id.action_jump_to_line_start) {
            mEditor.jumpToLineStart();
            return true;
        }
        if (itemId == R.id.action_jump_to_line_end) {
            mEditor.jumpToLineEnd();
            return true;
        }
        return false;
    }

    private boolean onMoreOptionsSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_console) {
            return tryDoing(mEditorView::showConsole);
        }
        if (itemId == R.id.action_import_java_class) {
            importJavaPackageOrClass();
            return true;
        }
        if (itemId == R.id.action_editor_text_size) {
            return tryDoing(mEditorView::selectTextSize);
        }
        if (itemId == R.id.action_editor_pinch_to_zoom) {
            setPinchToZoomStrategy();
            return true;
        }
        if (itemId == R.id.action_editor_fx_symbols_settings) {
            startSymbolsSettingsActivity();
            return true;
        }
        if (itemId == R.id.action_editor_theme) {
            return tryDoing(mEditorView::selectEditorTheme);
        }
        if (itemId == R.id.action_open_by_other_apps) {
            return tryDoing(mEditorView::openByOtherApps);
        }
        if (itemId == R.id.action_file_details) {
            showFileDetails();
            return true;
        }
        if (itemId == R.id.action_build_apk) {
            startBuildApkActivity();
            return true;
        }
        return false;
    }

    private void importJavaPackageOrClass() {
        mEditor.getSelection()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> new ClassSearchDialogBuilder(mContext)
                        .setQuery(s)
                        .itemClick((dialog, item, pos) -> showClassSearchingItem(dialog, item))
                        .title(R.string.text_find_java_classes)
                        .show());
    }

    private void showClassSearchingItem(MaterialDialog dialog, ClassSearchingItem item) {
        String title;
        String desc;
        if (item instanceof ClassSearchingItem.ClassItem) {
            AndroidClass androidClass = ((ClassSearchingItem.ClassItem) item).getAndroidClass();
            title = androidClass.getClassName();
            desc = androidClass.getFullName();
        } else {
            title = ((ClassSearchingItem.PackageItem) item).getPackageName();
            desc = title;
        }
        new MaterialDialog.Builder(mContext)
                .title(title)
                .content(desc)
                .neutralText(R.string.text_view_docs)
                .neutralColorRes(R.color.dialog_button_hint)
                .negativeText(R.string.text_en_import)
                .negativeColorRes(R.color.dialog_button_hint)
                .positiveText(R.string.text_copy)
                .positiveColorRes(R.color.dialog_button_hint)
                .onPositive((ignored, which) -> {
                    ClipboardUtils.setClip(mContext, desc);
                    ViewUtils.showToast(mContext, R.string.text_already_copied_to_clip);
                    dialog.dismiss();
                })
                .onNegative((ignored, which) -> {
                    var executionInfo = JavaScriptFileSource.parseExecutionMode(mEditor.getText());
                    mEditor.insert(executionInfo.getLineno(), item.getImportText() + ";\n");
                })
                .onNeutral((ignored, which) -> IntentUtils.browse(
                        mContext,
                        item.getUrl(),
                        new ToastExceptionHolder(mContext)
                ))
                .onAny((ignored, which) -> dialog.dismiss())
                .show();
    }

    private void startBuildApkActivity() {
        BuildActivity.launch(mContext, mEditorView.uri.getPath());
    }

    private void setPinchToZoomStrategy() {
        String key = key(R.string.key_editor_pinch_to_zoom_strategy);

        String defItemKey = key(R.string.default_key_editor_pinch_to_zoom_strategy);
        String itemKey = Pref.getString(key, defItemKey);

        List<String> itemKeys = Arrays.asList(mContext.getResources().getStringArray(R.array.keys_editor_pinch_to_zoom_strategy));

        int defSelectedIndex = Math.max(0, itemKeys.indexOf(itemKey));

        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(R.string.text_pinch_to_zoom)
                .items(R.array.values_editor_pinch_to_zoom_strategy)
                .itemsCallbackSingleChoice(defSelectedIndex, (dialog, itemView, which, text) -> {
                    String newKey = itemKeys.get(which);
                    if (!Objects.equals(newKey, itemKey)) {
                        Pref.putString(key, newKey);
                        mEditorView.editor.notifyPinchToZoomStrategyChanged(newKey);
                    }
                    return true;
                })
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative((dialog, which) -> dialog.dismiss())
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .onPositive((dialog, which) -> dialog.dismiss())
                .autoDismiss(false);

        MaterialDialogExtensions.choiceWidgetThemeColor(builder);

        makeEditorPinchToZoomScaleViewUnderDev(builder, itemKeys.indexOf(key(R.string.key_editor_pinch_to_zoom_scale_view)));
    }

    private void makeEditorPinchToZoomScaleViewUnderDev(MaterialDialog.Builder builder, int i) {
        builder.itemsDisabledIndices(i);
        MaterialDialog built = builder.build();
        assert built.getItems() != null;
        built.getItems().set(i, built.getItems().get(i) + " (" + builder.getContext().getString(R.string.text_under_development).toLowerCase(Language.getPrefLanguage().getLocale()) + ")");
        built.show();
    }

    private void startSymbolsSettingsActivity() {
        ViewUtils.showToast(mContext, R.string.text_under_development_content);
    }

    private void jumpToLine() {
        mEditor.getLineCount()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showJumpDialog);
    }

    private void showJumpDialog(final int lineCount) {
        String hint = "1 - " + lineCount;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(mContext)
                .title(R.string.text_jump_to_line)
                .input(hint, "", (dialog, input) -> {
                    if (TextUtils.isEmpty(input)) {
                        return;
                    }
                    try {
                        int line = Math.max(Math.min(Integer.parseInt(input.toString()), lineCount), 1);
                        mEditor.jumpTo(line - 1, 0);
                    } catch (NumberFormatException ignored) {
                        /* Ignored. */
                    }
                })
                .inputType(InputType.TYPE_CLASS_NUMBER);
        builder.positiveColorRes(R.color.dialog_button_attraction);
        builder.negativeText(R.string.dialog_button_cancel);
        builder.negativeColorRes(R.color.dialog_button_default);
        MaterialDialogExtensions.widgetThemeColor(builder);
        builder.show();
    }

    private void showFileDetails() {
        var path = mEditorView.uri.getPath();
        EditableFileInfoDialogManager.showEditableFileInfoDialog(mContext, new File(path), mEditor::getText);
    }

    protected boolean copyLine() {
        return tryDoing(mEditor::copyLine);
    }

    protected boolean deleteLine() {
        return tryDoing(mEditor::deleteLine);
    }

    protected boolean paste() {
        return tryDoing(() -> {
            CharSequence clip = getClip();
            if (clip != null) {
                mEditor.insert(clip.toString());
            }
        });
    }

    @Nullable
    private CharSequence getClip() {
        return ClipboardUtils.getClip(mContext);
    }

    private void findOrReplace() {
        mEditor.getSelection()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> {
                    FindOrReplaceDialogBuilder builder = new FindOrReplaceDialogBuilder(mContext, mEditorView)
                            .setQueryIfNotEmpty(s);
                    builder.positiveColorRes(R.color.dialog_button_attraction);
                    builder.show();
                });
    }

    private void copyAll() {
        ClipboardUtils.setClip(mContext, mEditor.getText());
        ViewUtils.showSnack(mEditorView, R.string.text_already_copied_to_clip);
    }

    private boolean tryDoing(Runnable callable) {
        try {
            callable.run();
            return true;
        } catch (Exception ignore) {
            /* Ignored. */
        }
        return false;
    }

}
