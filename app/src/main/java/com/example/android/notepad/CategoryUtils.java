package com.example.android.notepad;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class CategoryUtils {
    // 预设分类列表
    public static final List<String> CATEGORIES = Arrays.asList(
            "默认", "工作", "行程", "学习", "灵感", "生活", "其他"
    );

    // 获取分类数量（从数据库查询，兼容低版本API）
    public static HashMap<String, Integer> getCategoryCounts(Context context) {
        HashMap<String, Integer> counts = new HashMap<>();
        // 初始化所有分类计数为0
        for (String cat : CATEGORIES) {
            counts.put(cat, 0);
        }

        // 直接获取数据库实例执行原生SQL（兼容低版本，无需setGroupBy）
        NotePadProvider.DatabaseHelper dbHelper = new NotePadProvider.DatabaseHelper(context);
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            // 打开可读数据库
            db = dbHelper.getReadableDatabase();
            // 原生SQL：按分类分组统计数量
            String sql = "SELECT " + NotePad.Notes.COLUMN_NAME_CATEGORY + ", COUNT(*) AS count " +
                    "FROM " + NotePad.Notes.TABLE_NAME + " " +
                    "GROUP BY " + NotePad.Notes.COLUMN_NAME_CATEGORY;

            // 执行原生查询
            cursor = db.rawQuery(sql, null);

            if (cursor != null && cursor.moveToFirst()) {
                // 遍历结果，更新分类数量
                do {
                    String cat = cursor.getString(0);
                    int count = cursor.getInt(1);
                    // 只更新预设分类列表中的分类（避免异常分类）
                    if (CATEGORIES.contains(cat)) {
                        counts.put(cat, count);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭游标和数据库，释放资源
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
            dbHelper.close();
        }

        // 计算全部数量（使用传统循环兼容低版本Java）
        int total = 0;
        for (Integer count : counts.values()) {
            total += count;
        }
        counts.put("全部", total);

        return counts;
    }
}