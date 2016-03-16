package company.electrobin.common;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

public class Installation {

    private static String mId;
    private static final String INSTALLATION = "INSTALLATION";

    /**
     *
     * @param context
     * @return
     */
    public synchronized static String id(Context context) {
        if (mId == null) {
            File file = new File(context.getFilesDir(), INSTALLATION);
            try {
                if (!file.exists()) writeFile(file);
                mId = readFile(file);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return mId;
    }

    /**
     *
     * @param file
     * @return
     * @throws IOException
     */
    private static String readFile(File file) throws IOException {
        RandomAccessFile f = new RandomAccessFile(file, "r");
        byte[] bytes = new byte[(int)f.length()];
        f.readFully(bytes);
        f.close();

        return new String(bytes);
    }

    /**
     *
     * @param file
     * @throws IOException
     */
    private static void writeFile(File file) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        String id = UUID.randomUUID().toString();
        out.write(id.getBytes());
        out.close();
    }
}
