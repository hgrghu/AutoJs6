# AI Agent 布局文件说明

## 需要创建的布局文件

### 1. activity_agent_control.xml

路径: `app/src/main/res/layout/activity_agent_control.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?attr/colorPrimary"
            android:theme="?attr/actionBarTheme"
            app:popupTheme="?attr/actionBarPopupTheme" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Agent状态显示 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp">

                <TextView
                    android:id="@+id/statusText"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:padding="16dp"
                    android:text="@string/text_agent_status"
                    android:textSize="12sp"
                    android:fontFamily="monospace" />

            </com.google.android.material.card.MaterialCardView>

            <!-- 任务输入区域 -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:hint="@string/text_describe_task">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/taskInput"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:maxLines="4"
                    android:inputType="textMultiLine" />

            </com.google.android.material.textfield.TextInputLayout>

            <!-- 控制按钮 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:orientation="horizontal">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/executeButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/text_execute"
                    app:icon="@drawable/ic_play_arrow" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/voiceInputButton"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    app:icon="@drawable/ic_mic" />

            </LinearLayout>

            <!-- 功能选项 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:orientation="horizontal">

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/autoExecuteSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/text_auto_execute" />

                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/learningModeSwitch"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:text="@string/text_learning_mode" />

            </LinearLayout>

            <!-- 功能按钮 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:orientation="horizontal">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/analyzeUIButton"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/text_analyze_ui"
                    app:icon="@drawable/ic_visibility" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/historyButton"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginStart="8dp"
                    android:text="@string/text_execution_history"
                    app:icon="@drawable/ic_history" />

            </LinearLayout>

            <!-- 进度条 -->
            <com.google.android.material.progressindicator.LinearProgressIndicator
                android:id="@+id/progressBar"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:visibility="gone"
                android:indeterminate="true" />

            <!-- 结果显示 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp">

                <TextView
                    android:id="@+id/resultText"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:padding="16dp"
                    android:text="@string/text_ready" />

            </com.google.android.material.card.MaterialCardView>

            <!-- UI分析信息 -->
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/uiAnalysisInfo"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:visibility="gone">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:padding="16dp"
                    android:textSize="12sp" />

            </com.google.android.material.card.MaterialCardView>

            <!-- 脚本预览 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

                <ScrollView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:maxHeight="300dp">

                    <TextView
                        android:id="@+id/scriptPreview"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:padding="16dp"
                        android:fontFamily="monospace"
                        android:textSize="12sp"
                        android:textIsSelectable="true" />

                </ScrollView>

            </com.google.android.material.card.MaterialCardView>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### 2. activity_ui_analyzer.xml

路径: `app/src/main/res/layout/activity_ui_analyzer.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?attr/colorPrimary"
            android:theme="?attr/actionBarTheme"
            app:popupTheme="?attr/actionBarPopupTheme" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- 控制按钮 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:orientation="horizontal">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/analyzeButton"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/text_analyze_ui"
                    app:icon="@drawable/ic_visibility" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/refreshButton"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    app:icon="@drawable/ic_refresh" />

            </LinearLayout>

            <!-- 进度条 -->
            <com.google.android.material.progressindicator.LinearProgressIndicator
                android:id="@+id/progressBar"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp"
                android:visibility="gone"
                android:indeterminate="true" />

            <!-- 状态显示 -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="16dp">

                <TextView
                    android:id="@+id/statusText"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:padding="16dp"
                    android:text="@string/text_ready" />

            </com.google.android.material.card.MaterialCardView>

            <!-- 分析结果容器 -->
            <LinearLayout
                android:id="@+id/resultContainer"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:visibility="gone">

                <!-- 元素统计 -->
                <com.google.android.material.card.MaterialCardView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="16dp">

                        <TextView
                            android:id="@+id/elementsCountText"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textStyle="bold" />

                        <TextView
                            android:id="@+id/confidenceText"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="8dp" />

                    </LinearLayout>

                </com.google.android.material.card.MaterialCardView>

                <!-- 建议操作 -->
                <com.google.android.material.card.MaterialCardView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="16dp">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="@string/text_suggested_actions"
                            android:textStyle="bold"
                            android:layout_marginBottom="8dp" />

                        <TextView
                            android:id="@+id/suggestionsText"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:textSize="14sp" />

                    </LinearLayout>

                </com.google.android.material.card.MaterialCardView>

                <!-- 生成的代码 -->
                <com.google.android.material.card.MaterialCardView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:padding="16dp">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="@string/text_generated_code"
                            android:textStyle="bold"
                            android:layout_marginBottom="8dp" />

                        <ScrollView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:maxHeight="300dp">

                            <TextView
                                android:id="@+id/codePreview"
                                android:layout_width="match_parent"
                                android:layout_height="wrap_content"
                                android:fontFamily="monospace"
                                android:textSize="12sp"
                                android:textIsSelectable="true" />

                        </ScrollView>

                    </LinearLayout>

                </com.google.android.material.card.MaterialCardView>

            </LinearLayout>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

## 需要的图标资源

在 `app/src/main/res/drawable/` 目录下需要添加以下图标：

1. `ic_smart_toy.xml` - AI Agent图标
2. `ic_mic.xml` - 麦克风图标  
3. `ic_visibility.xml` - 眼睛/查看图标
4. `ic_history.xml` - 历史记录图标
5. `ic_refresh.xml` - 刷新图标
6. `ic_play_arrow.xml` - 播放/执行图标

这些图标可以从 Material Design Icons 获取，或者使用 Android Studio 的 Vector Asset 工具生成。

## 注意事项

1. 布局文件使用了 Material Design 组件，确保项目已包含 Material Design 库
2. 所有字符串资源都已在 `strings.xml` 中定义
3. 布局使用了响应式设计，支持不同屏幕尺寸
4. 代码预览区域使用了等宽字体和可选择文本
5. 使用了卡片布局提供更好的视觉层次

## 创建完这些文件后的测试

1. 编译项目确保没有错误
2. 在主界面菜单中点击 AI Agent 图标
3. 测试自然语言输入功能
4. 测试UI分析功能
5. 检查Agent状态显示是否正常

通过这些布局文件，AI Agent 功能将完全集成到 AutoJs6 的用户界面中。