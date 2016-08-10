package company.electrobin.common.route;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import company.electrobin.common.Serializable;

final class RouteContract {
    // To prevent someone from accidentally instantiating the contract class,
    // give it an empty constructor.
    public RouteContract() {}

    static abstract class RouteEntry implements BaseColumns {
        static final String TABLE_NAME = "route";
        static final String COLUMN_NAME_SERIALIZED_DATA = "serialized_data";
    }
}

public class RouteDbHelper extends SQLiteOpenHelper {
    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES =
        "CREATE TABLE " + RouteContract.RouteEntry.TABLE_NAME + " (" +
            // RouteContract.RouteEntry._ID + " INTEGER PRIMARY KEY," +
            RouteContract.RouteEntry.COLUMN_NAME_SERIALIZED_DATA + TEXT_TYPE +
            // FeedEntry.COLUMN_NAME_TITLE + TEXT_TYPE + COMMA_SEP +
        " )";

    private static final String SQL_DELETE_ENTRIES =
        "DROP TABLE IF EXISTS " + RouteContract.RouteEntry.TABLE_NAME;

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "Route.db";

    public RouteDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    public void store(Serializable route) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(RouteContract.RouteEntry.COLUMN_NAME_SERIALIZED_DATA, route.serialize());

        db.replace(RouteContract.RouteEntry.TABLE_NAME, null, values);
    }

    public String retrieve() {
        SQLiteDatabase db = getReadableDatabase();
        String[] projection = {
            RouteContract.RouteEntry.COLUMN_NAME_SERIALIZED_DATA
        };

        Cursor c = db.query(
            RouteContract.RouteEntry.TABLE_NAME,
            projection,                               // The columns to return
            null,                                     // The columns for the WHERE clause
            null,                                     // The values for the WHERE clause
            null,                                     // Group the rows
            null,                                     // Filter by row groups
            null                                      // The sort order
        );

        if (c.getCount() == 0) return null;
        final String result = c.getString(0);
        c.close();

        return result;
    }

    public void delete() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(RouteContract.RouteEntry.TABLE_NAME, null, null);
    }
}
