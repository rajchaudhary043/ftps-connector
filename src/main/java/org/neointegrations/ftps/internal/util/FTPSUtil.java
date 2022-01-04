package org.neointegrations.ftps.internal.util;

import org.mule.extension.file.common.api.matcher.FileMatcher;
import org.mule.extension.file.common.api.matcher.NullFilePayloadPredicate;
import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.api.FTPSFileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

public class FTPSUtil {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSUtil.class);
    private final static DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("YYYYMMddhhmmssSSS");
    private final static String TIME_STAMP_DEFAULT_STR = "TS";

    public static Predicate<FTPSFileAttributes> getPredicate(FileMatcher builder) {
        return (Predicate) (builder != null ? builder.build() : new NullFilePayloadPredicate());
    }

    public static String trimPath(String directory, String fileName) {
        String dir = directory;
        if (dir.endsWith("/") || dir.endsWith("\\")) {
            dir = dir.substring(0, dir.length() - 1);
        }
        return directory + File.separator + fileName;
    }

    public static void close(AutoCloseable closeable) {
        try {
            if(closeable != null) closeable.close();
        } catch (Exception e) {
            _logger.warn("An exception occurred while closing {}", closeable, e);
        }
    }

    public static String makeIntermediateFileName(LocalDateTime timestamp, String fName) {
        if(fName == null) return fName;
        String tsStr = timestamp(timestamp);
        return "__" + tsStr + "_" + fName;
    }
    public static String makeIntermediateFileName(String timestamp, String fName) {
        if(fName == null) return fName;
        String tsStr = TIME_STAMP_DEFAULT_STR;
        if(timestamp != null) {
            tsStr = timestamp;
        }
        return "__" + tsStr + "_" + fName;
    }
    public static String timestamp(LocalDateTime timestamp){
        if(timestamp != null) {
            return timestamp.format(TS_FORMATTER);
        } else {
            return TIME_STAMP_DEFAULT_STR;
        }
    }

    public static InputStream getStream(String fileName) throws ConnectionException {
        if(fileName == null) throw new NullPointerException("fileName can not be null");
        InputStream is = FTPSUtil.class.getClassLoader().getResourceAsStream(fileName);
        if (is == null) {
            if(_logger.isDebugEnabled()) {
                _logger.debug("File does not exists in the classpath, so checking for absolute path");
            }
            try {
                is = Files.newInputStream(Paths.get(fileName));
            }catch(Exception exp) {
                _logger.error("Could not load the file: {}", exp.getMessage(), exp);
                throw new ConnectionException(exp);
            }
        }
        return is;
    }

    private static final char[] hexDigits = "0123456789ABCDEF".toCharArray();
    public static String toHexString(byte[] var0) {
        if (var0 != null && var0.length != 0) {
            StringBuilder var1 = new StringBuilder(var0.length * 3);
            boolean var2 = true;
            byte[] var3 = var0;
            int var4 = var0.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                byte var6 = var3[var5];
                if (var2) {
                    var2 = false;
                } else {
                    var1.append(' ');
                }

                var1.append(hexDigits[var6 >> 4 & 15]);
                var1.append(hexDigits[var6 & 15]);
            }

            return var1.toString();
        } else {
            return "";
        }
    }

}
