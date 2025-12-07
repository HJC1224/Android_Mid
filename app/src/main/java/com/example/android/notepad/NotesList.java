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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.content.CursorLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays a list of notes with category filtering and search functionality.
 */
public class NotesList extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private LinearLayout mSearchBar;
    private EditText mSearchEditText;
    private Button mSearchButton;
    private Button mCancelButton;
    private String mSearchQuery;

    // 新增分类相关变量
    private String mSelectedCategory = "全部"; // 默认选中全部
    private HashMap<String, Integer> mCategoryCounts;
    private TextView mCategoryTitle;
    private ImageView mCategoryDropdown;

    private SimpleCursorAdapter mAdapter;
    private TextWatcher mSearchTextWatcher;

    private static final String TAG = "NotesList";
    private static final int LOADER_ID = 1;
    private boolean isRealTimeSearch = true;

    /**
     * 投影字段添加分类列
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
            NotePad.Notes.COLUMN_NAME_NOTE, // 3
            NotePad.Notes.COLUMN_NAME_CREATE_DATE, // 4
            NotePad.Notes.COLUMN_NAME_CATEGORY // 5 新增分类字段
    };

    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 2;
    private static final int COLUMN_INDEX_CREATE_DATE = 4;
    private static final int COLUMN_INDEX_CATEGORY = 5; // 分类字段索引

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notes_list);

        // 配置自定义标题栏
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            View customActionBar = LayoutInflater.from(this).inflate(R.layout.custom_action_bar, null);
            actionBar.setCustomView(customActionBar);

            ImageButton brushBtn = (ImageButton) customActionBar.findViewById(R.id.btn_brush);
            brushBtn.setOnClickListener(v -> showColorPickerDialog());
        }

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        // 初始化分类栏
        initCategoryBar();

        // 初始化适配器
        mAdapter = new SimpleCursorAdapter(
                this,
                R.layout.noteslist_item,
                null,
                new String[]{},
                new int[]{},
                0
        ) {
            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                super.bindView(view, context, cursor);

                // 1. 处理标题
                String title = cursor.getString(COLUMN_INDEX_TITLE);
                TextView titleView = (TextView) view.findViewById(android.R.id.text1);
                if (titleView != null) {
                    String displayTitle = TextUtils.isEmpty(title) ? getString(R.string.undefined_title) : title;
                    titleView.setText(displayTitle);
                    titleView.setSingleLine(true);
                    titleView.setEllipsize(TextUtils.TruncateAt.END);
                }

                // 2. 处理正文内容
                String content = cursor.getString(cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE));
                TextView line1 = (TextView) view.findViewById(R.id.content_line1);
                TextView line2 = (TextView) view.findViewById(R.id.content_line2);
                TextView line3 = (TextView) view.findViewById(R.id.content_line3);
                TextView contentEllipsis = (TextView) view.findViewById(R.id.content_ellipsis);

                line1.setText("");
                line2.setText("");
                line3.setText("");
                contentEllipsis.setVisibility(View.GONE);

                int textColor = getResources().getColor(R.color.note_content_color);
                float textSize = 14;

                line1.setSingleLine(true);
                line1.setEllipsize(TextUtils.TruncateAt.END);
                line1.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                line1.setTextColor(textColor);
                line1.setPadding(0, 4, 0, 4);

                line2.setSingleLine(true);
                line2.setEllipsize(TextUtils.TruncateAt.END);
                line2.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                line2.setTextColor(textColor);
                line2.setPadding(0, 4, 0, 4);

                line3.setSingleLine(true);
                line3.setEllipsize(TextUtils.TruncateAt.END);
                line3.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
                line3.setTextColor(textColor);
                line3.setPadding(0, 4, 0, 4);

                if (!TextUtils.isEmpty(content)) {
                    line1.setPaintFlags(line1.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    line2.setPaintFlags(line2.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    line3.setPaintFlags(line3.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

                    String[] originalLines = content.split("\n");
                    List<String> contentLines = new ArrayList<>();

                    for (String line : originalLines) {
                        String remaining = line;
                        while (remaining.length() > 30) {
                            contentLines.add(remaining.substring(0, 30));
                            remaining = remaining.substring(30);
                        }
                        if (!remaining.isEmpty()) {
                            contentLines.add(remaining);
                        }
                    }

                    if (!contentLines.isEmpty()) {
                        line1.setText(contentLines.get(0));
                    }
                    if (contentLines.size() > 1) {
                        line2.setText(contentLines.get(1));
                    }
                    if (contentLines.size() > 2) {
                        line3.setText(contentLines.get(2));
                    }

                    if (contentLines.size() > 3) {
                        contentEllipsis.setVisibility(View.VISIBLE);
                    } else {
                        contentEllipsis.setVisibility(View.INVISIBLE);
                    }

                } else {
                    line1.setPaintFlags(line1.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
                    line1.setText("暂无正文内容");
                    line2.setPaintFlags(line2.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
                    line3.setPaintFlags(line3.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
                }

                // 3. 处理搜索匹配信息
                TextView searchLocationView = (TextView) view.findViewById(R.id.text_search_location);
                TextView contentMatchView = (TextView) view.findViewById(R.id.text_content_match);

                searchLocationView.setVisibility(View.GONE);
                contentMatchView.setVisibility(View.GONE);

                if (!TextUtils.isEmpty(mSearchQuery) && (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(content))) {
                    boolean titleHasMatch = !TextUtils.isEmpty(title) && title.contains(mSearchQuery);
                    boolean contentHasMatch = !TextUtils.isEmpty(content) && content.contains(mSearchQuery);

                    SpannableString matchContextText = null;
                    String locationText = "";

                    if (titleHasMatch) {
                        locationText = "搜索词在标题部分：";
                        String contextText = getKeywordContext(title, mSearchQuery);
                        matchContextText = new SpannableString(contextText);
                        int start = contextText.indexOf(mSearchQuery);
                        int end = start + mSearchQuery.length();
                        matchContextText.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        matchContextText.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (contentHasMatch) {
                        String[] originalLines = content.split("\n");
                        int lineNumber = -1;
                        String lineWithKeyword = "";
                        for (int i = 0; i < originalLines.length; i++) {
                            if (originalLines[i].contains(mSearchQuery)) {
                                lineNumber = i + 1;
                                lineWithKeyword = originalLines[i];
                                break;
                            }
                        }

                        if (lineNumber != -1) {
                            locationText = "搜索词在正文部分，第" + lineNumber + "行：";
                            String contextText = getKeywordContext(lineWithKeyword, mSearchQuery);
                            matchContextText = new SpannableString(contextText);
                            int start = contextText.indexOf(mSearchQuery);
                            int end = start + mSearchQuery.length();
                            matchContextText.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            matchContextText.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    if (matchContextText != null && !TextUtils.isEmpty(locationText)) {
                        searchLocationView.setText(locationText);
                        searchLocationView.setVisibility(View.VISIBLE);
                        contentMatchView.setText(matchContextText);
                        contentMatchView.setVisibility(View.VISIBLE);
                    }
                }

                // 4. 处理时间显示
                long createTimeMillis = cursor.getLong(COLUMN_INDEX_CREATE_DATE);
                long modifyTimeMillis = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);

                TextView createTimeView = (TextView)view.findViewById(R.id.text_create_time);
                String createTimeStr = "创建时间：" + DateUtils.formatTime(createTimeMillis);
                createTimeView.setText(createTimeStr);
                createTimeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                TextView modifyTimeView = (TextView)view.findViewById(R.id.text_modify_time);
                String absoluteModifyTime = DateUtils.formatTime(modifyTimeMillis);
                String modifyTimeStr = "修改时间：" + absoluteModifyTime;
                modifyTimeView.setText(modifyTimeStr);
                modifyTimeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

                // 5. 设置分类标签（替换原有"默认"文本）
                String category = cursor.getString(COLUMN_INDEX_CATEGORY);
                TextView categoryLabel =  (TextView) view.findViewById(R.id.category_label);
                categoryLabel.setText(category != null ? category : "默认");
            }
        };

        setListAdapter(mAdapter);

        // 初始化搜索栏
        mSearchBar = (LinearLayout) findViewById(R.id.search_bar);
        mSearchEditText = (EditText) findViewById(R.id.search_edit_text);
        mSearchButton = (Button) findViewById(R.id.search_button);
        mCancelButton = (Button) findViewById(R.id.cancel_button);

        if (mSearchEditText == null) {
            Log.e(TAG, "mSearchEditText 初始化失败，请检查布局文件中的 search_edit_text 控件ID");
        }

        if (mSearchBar != null) {
            mSearchBar.setVisibility(View.GONE);
        }

        // 初始化搜索文本监听器
        mSearchTextWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isRealTimeSearch) {
                    String query = s.toString().trim();
                    if (TextUtils.isEmpty(query)) {
                        clearSearch();
                    } else {
                        mSearchQuery = query;
                        performSearch();
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (mSearchButton != null) {
            mSearchButton.setOnClickListener(v -> {
                if (isRealTimeSearch) {
                    performSearch();
                } else {
                    String query = mSearchEditText.getText().toString().trim();
                    mSearchQuery = query;
                    performSearch();
                }
            });
        }

        if (mCancelButton != null) {
            mCancelButton.setOnClickListener(v -> hideSearchBar());
        }

        if (mSearchEditText != null) {
            mSearchEditText.addTextChangedListener(mSearchTextWatcher);

            mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
                    return true;
                }
                return false;
            });
        }

        // 加载分类数量并初始化Loader
        loadCategoryCounts();
        getLoaderManager().initLoader(LOADER_ID, null, this);
    }

    /**
     * 初始化分类选择栏
     */
    private void initCategoryBar() {
        mCategoryTitle = (TextView) findViewById(R.id.category_title);
        mCategoryDropdown = (ImageView) findViewById(R.id.category_dropdown);

        if (mCategoryTitle != null && mCategoryDropdown != null) {
            mCategoryDropdown.setOnClickListener(v -> showCategoryPopup());
        } else {
            Log.e(TAG, "分类栏控件初始化失败，请检查布局文件");
        }
    }

    /**
     * 显示分类选择弹窗
     */
    private void showCategoryPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择分类");

        List<String> items = new ArrayList<>();
        mCategoryCounts = CategoryUtils.getCategoryCounts(this);

        // 构建分类列表（包含数量）
        for (String cat : CategoryUtils.CATEGORIES) {
            int count = mCategoryCounts.containsKey(cat) ? mCategoryCounts.get(cat) : 0;
            items.add(cat + " (" + count + ")");

        }
        // 插入"全部"选项到首位
        int totalCount = mCategoryCounts.containsKey("全部") ? mCategoryCounts.get("全部") : 0;
        items.add(0, "全部 (" + totalCount + ")");

        builder.setItems(items.toArray(new String[0]), (dialog, which) -> {
            mSelectedCategory = which == 0 ? "全部" : CategoryUtils.CATEGORIES.get(which - 1);
            // 更新分类显示文本
            if (mCategoryTitle != null) {
                mCategoryTitle.setText("分类显示: " + mSelectedCategory);
            }
            performSearch(); // 执行搜索（包含分类筛选）
        });
        builder.show();
    }

    /**
     * 加载分类数量统计
     */
    private void loadCategoryCounts() {
        mCategoryCounts = CategoryUtils.getCategoryCounts(this);
        // 初始化分类显示文本
        if (mCategoryTitle != null) {
            mCategoryTitle.setText("分类显示: " + mSelectedCategory);
        }
    }

    private void showColorPickerDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_color_picker);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        View white = dialog.findViewById(R.id.color_white);
        View green = dialog.findViewById(R.id.color_green);
        View blue = dialog.findViewById(R.id.color_blue);
        View yellow = dialog.findViewById(R.id.color_yellow);
        View pink = dialog.findViewById(R.id.color_pink);

        View.OnClickListener colorClickListener = v -> {
            int colorId = R.color.light_white;
            int viewId = v.getId();

            if (viewId == R.id.color_white) {
                colorId = R.color.light_white;
            } else if (viewId == R.id.color_green) {
                colorId = R.color.light_green;
            } else if (viewId == R.id.color_blue) {
                colorId = R.color.light_blue;
            } else if (viewId == R.id.color_yellow) {
                colorId = R.color.light_yellow;
            } else if (viewId == R.id.color_pink) {
                colorId = R.color.light_pink;
            }

            LinearLayout rootLayout = (LinearLayout) findViewById(R.id.root_layout);
            rootLayout.setBackgroundColor(getResources().getColor(colorId));
            dialog.dismiss();
        };

        white.setOnClickListener(colorClickListener);
        green.setOnClickListener(colorClickListener);
        blue.setOnClickListener(colorClickListener);
        yellow.setOnClickListener(colorClickListener);
        pink.setOnClickListener(colorClickListener);

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }

    private SpannableString addUnderlineToKeyword(String text, String keyword) {
        SpannableString spannable = new SpannableString(text);
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(keyword)) {
            return spannable;
        }
        int start = text.indexOf(keyword);
        while (start != -1) {
            int end = start + keyword.length();
            spannable.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = text.indexOf(keyword, end);
        }
        return spannable;
    }

    private int findKeywordLine(String content, String keyword) {
        if (TextUtils.isEmpty(content) || TextUtils.isEmpty(keyword)) return 0;
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(keyword)) {
                return i + 1;
            }
        }
        return 0;
    }

    private String getKeywordContext(String lineText, String keyword) {
        if (TextUtils.isEmpty(lineText) || TextUtils.isEmpty(keyword)) {
            return "";
        }

        int startIndex = lineText.indexOf(keyword);
        if (startIndex == -1) {
            return "";
        }

        int contextLength = 5;
        int start = Math.max(0, startIndex - contextLength);
        int end = Math.min(lineText.length(), startIndex + keyword.length() + contextLength);

        String context = lineText.substring(start, end);
        if (start > 0) {
            context = "..." + context;
        }
        if (end < lineText.length()) {
            context += "...";
        }

        return context;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        if (clipboard != null && clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            mPasteItem.setEnabled(false);
        }

        final boolean haveItems = mAdapter.getCount() > 0;

        if (haveItems) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());
            Intent[] specifics = new Intent[1];
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);
            MenuItem[] items = new MenuItem[1];
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,
                    Menu.NONE,
                    Menu.NONE,
                    null,
                    specifics,
                    intent,
                    Menu.NONE,
                    items
            );

            if (items[0] != null) {
                items[0].setShortcut('1', 'e');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_add) {
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_INSERT);
            intent.setData(getIntent().getData());
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_paste) {
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_PASTE);
            intent.setData(getIntent().getData());
            startActivity(intent);
            return true;
        } else if (itemId == R.id.menu_search) {
            showSearchModePopup(findViewById(R.id.menu_search));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showSearchModePopup(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.search_mode_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_real_time_search) {
                isRealTimeSearch = true;
                showSearchBar();
                updateSearchButtons();
                mSearchEditText.removeTextChangedListener(mSearchTextWatcher);
                mSearchEditText.addTextChangedListener(mSearchTextWatcher);
                mSearchQuery = mSearchEditText.getText().toString().trim();
                performSearch();
                return true;
            } else if (itemId == R.id.menu_click_search) {
                isRealTimeSearch = false;
                showSearchBar();
                updateSearchButtons();
                mSearchEditText.removeTextChangedListener(mSearchTextWatcher);
                clearSearch();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showSearchBar() {
        if (mSearchBar != null) {
            mSearchBar.setVisibility(View.VISIBLE);
            mSearchEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT);
            updateSearchButtons();
        }
    }

    private void updateSearchButtons() {
        if (mSearchButton != null && mCancelButton != null) {
            if (isRealTimeSearch) {
                mSearchButton.setVisibility(View.GONE);
                mCancelButton.setVisibility(View.VISIBLE);
            } else {
                mSearchButton.setVisibility(View.VISIBLE);
                mCancelButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideSearchBar() {
        if (mSearchBar != null) {
            mSearchBar.setVisibility(View.GONE);
            if (mSearchEditText != null) {
                mSearchEditText.setText("");
            }
            mSearchQuery = null;
            restartLoader();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), 0);
        }
    }

    private void restartLoader() {
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    /**
     * 执行搜索（结合分类筛选和关键词搜索）
     */
    private void performSearch() {
        if (mSearchEditText == null) {
            Log.w(TAG, "mSearchEditText 为 null，无法执行搜索");
            return;
        }

        mSearchQuery = mSearchEditText.getText().toString().trim();
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    private void clearSearch() {
        if (mAdapter == null || mSearchEditText == null) return;

        mSearchEditText.removeTextChangedListener(mSearchTextWatcher);
        mSearchEditText.setText("");
        if (isRealTimeSearch) {
            mSearchEditText.addTextChangedListener(mSearchTextWatcher);
        }

        mSearchQuery = null;
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(info.position);
        if (cursor == null) {
            return;
        }

        // 获取标题并处理空值情况
        String title = cursor.getString(COLUMN_INDEX_TITLE);
        if (TextUtils.isEmpty(title)) {
            // 使用已定义的"未定义标题"字符串资源
            title = getString(R.string.undefined_title);
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
        menu.setHeaderTitle(title);

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        int id = item.getItemId();
        if (id == R.id.context_open) {
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(noteUri);
            startActivity(intent);
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(),
                    "Note",
                    noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(
                    noteUri,
                    null,
                    null
            );
            // 删除后更新分类数量
            loadCategoryCounts();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            Intent intent = new Intent(this, NoteEditor.class);
            intent.setAction(Intent.ACTION_EDIT);
            intent.setData(uri);
            startActivity(intent);
        }
    }

    /**
     * 创建Loader，结合分类和搜索条件
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = null;
        String[] selectionArgs = null;

        // 1. 处理分类筛选条件
        if (!"全部".equals(mSelectedCategory)) {
            selection = NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?";
            selectionArgs = new String[]{mSelectedCategory};
        }

        // 2. 处理搜索条件
        if (!TextUtils.isEmpty(mSearchQuery)) {
            String searchSelection = NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?";
            String[] searchArgs = new String[]{
                    "%" + mSearchQuery + "%",
                    "%" + mSearchQuery + "%"
            };

            // 合并分类和搜索条件
            if (selection == null) {
                selection = searchSelection;
                selectionArgs = searchArgs;
            } else {
                selection += " AND (" + searchSelection + ")";
                selectionArgs = new String[]{
                        mSelectedCategory,
                        "%" + mSearchQuery + "%",
                        "%" + mSearchQuery + "%"
                };
            }
        }

        return new CursorLoader(
                this,
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mAdapter.changeCursor(data);
        // 加载完成后更新分类数量
        loadCategoryCounts();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.changeCursor(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //  resume时刷新分类数量
        loadCategoryCounts();
    }
}