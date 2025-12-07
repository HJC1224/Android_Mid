/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.List;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID, title, note contents and category.
     */
    private static final String[] PROJECTION =
            new String[] {
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE,
                    NotePad.Notes.COLUMN_NAME_CATEGORY // 新增分类列
            };

    // 定义列索引
    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_NOTE = 2;
    private static final int COLUMN_INDEX_CATEGORY = 3; // 新增分类列索引

    // 保存状态的标签
    private static final String ORIGINAL_CONTENT = "origContent";
    private static final String ORIGINAL_TITLE = "origTitle";
    private static final String ORIGINAL_CATEGORY = "origCategory"; // 新增分类保存标签

    // 活动状态常量
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // 全局变量
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mTitleText; // 标题输入框
    private EditText mNoteText;  // 正文输入框
    private String mOriginalTitle;
    private String mOriginalContent;
    private String mOriginalCategory; // 新增原始分类
    private String mCurrentCategory = "默认"; // 当前分类，默认"默认"
    private TextView mCategoryView; // 分类显示视图
    // 新增：控制是否忽略保存的标志位
    private boolean mIgnoreSave = false;

    /**
     * 自定义带行线的EditText
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;

        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int count = getLineCount();
            Rect r = mRect;
            Paint paint = mPaint;

            for (int i = 0; i < count; i++) {
                int baseline = getLineBounds(i, r);
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            super.onDraw(canvas);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // 初始化状态和URI
        if (Intent.ACTION_EDIT.equals(action)) {
            mState = STATE_EDIT;
            mUri = intent.getData();
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {
            mState = STATE_INSERT;

            // 插入新笔记记录（包含默认分类）
            ContentValues initialValues = new ContentValues();
            long currentTime = System.currentTimeMillis();
            initialValues.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, currentTime);
            initialValues.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, currentTime);
            initialValues.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
            initialValues.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
            initialValues.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCurrentCategory); // 新增默认分类

            mUri = getContentResolver().insert(intent.getData(), initialValues);

            if (mUri == null) {
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());
                finish();
                return;
            }

            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
        } else {
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        // 查询笔记数据（包含分类）
        mCursor = managedQuery(
                mUri,
                PROJECTION,
                null,
                null,
                null
        );

        // 处理粘贴操作
        if (Intent.ACTION_PASTE.equals(action)) {
            performPaste();
            mState = STATE_EDIT;
        }

        // 设置布局和输入框
        setContentView(R.layout.note_editor);
        mTitleText = (EditText) findViewById(R.id.title);
        mNoteText = (EditText) findViewById(R.id.note);

        // 初始化分类视图
        mCategoryView = (TextView) findViewById(R.id.current_category);
        findViewById(R.id.select_category).setOnClickListener(v -> showCategoryDialog());

        // 恢复保存的状态
        if (savedInstanceState != null) {
            mOriginalTitle = savedInstanceState.getString(ORIGINAL_TITLE);
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
            mOriginalCategory = savedInstanceState.getString(ORIGINAL_CATEGORY);
            mCurrentCategory = mOriginalCategory;
            mCategoryView.setText(mCurrentCategory);
        }

        // 设置标题栏文本
        setTitleBarText();
    }

    /**
     * 设置标题栏显示文本
     */
    private void setTitleBarText() {
        if (mState == STATE_INSERT || Intent.ACTION_PASTE.equals(getIntent().getAction())) {
            setTitle(R.string.title_create);
        } else {
            setTitle(R.string.title_edit);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {
            mCursor.requery();
            mCursor.moveToFirst();

            // 加载标题、正文内容和分类
            String title = mCursor.getString(COLUMN_INDEX_TITLE);
            String content = mCursor.getString(COLUMN_INDEX_NOTE);
            String category = mCursor.getString(COLUMN_INDEX_CATEGORY);

            mTitleText.setText(title);
            mNoteText.setTextKeepState(content);
            mCurrentCategory = category;
            mCategoryView.setText(mCurrentCategory);

            // 保存原始内容用于判断修改
            if (mOriginalTitle == null) {
                mOriginalTitle = title;
            }
            if (mOriginalContent == null) {
                mOriginalContent = content;
            }
            if (mOriginalCategory == null) {
                mOriginalCategory = category;
            }
        } else {
            setTitle(getText(R.string.error_title));
            mNoteText.setText(getText(R.string.error_message));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(ORIGINAL_TITLE, mOriginalTitle);
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
        outState.putString(ORIGINAL_CATEGORY, mOriginalCategory); // 保存原始分类
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // 新增：如果是返回操作则忽略保存
        if (mIgnoreSave) {
            return;
        }

        if (mCursor != null) {
            String currentTitle = mTitleText.getText().toString();
            String currentContent = mNoteText.getText().toString();

            // 内容或分类有变化才更新
            if (!currentTitle.equals(mOriginalTitle)
                    || !currentContent.equals(mOriginalContent)
                    || !mCurrentCategory.equals(mOriginalCategory)) {
                updateNote(currentContent, currentTitle);
                mOriginalTitle = currentTitle;
                mOriginalContent = currentContent;
                mOriginalCategory = mCurrentCategory;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCursor != null) {
            menu.findItem(R.id.menu_back).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        // 新增：返回按钮处理逻辑
        if (id == R.id.menu_back) {
            // 新建状态：直接删除空笔记
            if (mState == STATE_INSERT) {
                deleteNote();
            } else {
                // 编辑状态：恢复原始内容（包括分类）
                mTitleText.setText(mOriginalTitle);
                mNoteText.setText(mOriginalContent);
                mCurrentCategory = mOriginalCategory;
                mCategoryView.setText(mCurrentCategory);
            }
            mIgnoreSave = true; // 标记忽略保存
            finish(); // 返回主页
            return true;
        } else if (id == R.id.menu_save) {
            String title = mTitleText.getText().toString();
            String content = mNoteText.getText().toString();
            updateNote(content, title);
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_back) {
            cancelNote();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 处理粘贴操作，将内容放入正文
     */
    private void performPaste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            String pasteText = clip.getItemAt(0).coerceToText(this).toString();
            mNoteText.setText(pasteText);
        }
    }

    /**
     * 更新笔记内容、标题和分类
     */
    private void updateNote(String content, String title) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, content);
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mCurrentCategory); // 新增分类更新

        getContentResolver().update(mUri, values, null, null);
    }

    /**
     * 取消编辑，恢复原始内容（包括分类）
     */
    private void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_TITLE, mOriginalTitle);
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, mOriginalCategory); // 恢复分类
                getContentResolver().update(mUri, values, null, null);

                mTitleText.setText(mOriginalTitle);
                mNoteText.setText(mOriginalContent);
                mCurrentCategory = mOriginalCategory;
                mCategoryView.setText(mCurrentCategory);
            } else if (mState == STATE_INSERT) {
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
    }

    /**
     * 删除笔记
     */
    private void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mTitleText.setText("");
            mNoteText.setText("");
            mCategoryView.setText("");
        }
    }

    /**
     * 显示分类选择对话框
     */
    private void showCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择类别");
        // 假设CategoryUtils包含静态分类列表
        String[] categories = CategoryUtils.CATEGORIES.toArray(new String[0]);
        builder.setItems(categories, (dialog, which) -> {
            mCurrentCategory = categories[which];
            mCategoryView.setText(mCurrentCategory);
        });
        builder.show();
    }
}