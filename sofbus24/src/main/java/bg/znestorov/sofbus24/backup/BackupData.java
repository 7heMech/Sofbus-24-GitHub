package bg.znestorov.sofbus24.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import bg.znestorov.sofbus24.utils.Utils;

/**
 * The methods in the class are used to save/load the user information in case
 * of reinstalling the application, deleting the data and etc.
 *
 * <p>This class has been migrated to the Storage Access Framework (SAF).
 * The user-facing source/target locations are no longer raw {@link File} paths
 * but {@link Uri}s obtained through {@code ACTION_OPEN_DOCUMENT} /
 * {@code ACTION_CREATE_DOCUMENT}. All I/O against those URIs is performed
 * through {@link ContentResolver#openInputStream(Uri)} and
 * {@link ContentResolver#openOutputStream(Uri)} so that the implementation is
 * compatible with Android 10+ Scoped Storage and is required on Android 14+.</p>
 *
 * <p>The internal compression / decompression steps still rely on
 * {@link ZipFile} which requires a {@link File} on disk. To stay within the
 * scoped-storage rules we stage those files inside the application's private
 * cache directory ({@link Context#getCacheDir()}) and remove them as soon as
 * the operation completes.</p>
 *
 * @author Zdravko Nestorov
 * @version 2.0
 */
class BackupData {

    /**
     * Locations of the files that MUST be excluded when making the backup:
     * -> "/data/data/bg.znestorov.sofbus24.main/databases"
     * -> "/data/data/bg.znestorov.sofbus24.main/databases/sofbus24.db"
     * -> "/data/data/bg.znestorov.sofbus24.main/databases/sofbus24.db-journal"
     * -> "/data/data/bg.znestorov.sofbus24.main/shared_prefs/application_gcm.xml"
     * -> "/data/data/bg.znestorov.sofbus24.main/files/gaClientId"
     */
    private static final String SOFBUS_24_DB_FOLDER_NAME = "databases";
    private static final String SOFBUS_24_DB_FILE = "sofbus24.db";
    private static final String SOFBUS_24_DB_JOURNAL_FILE = "sofbus24.db-journal";
    private static final String SOFBUS_24_PREF_GCM_FILE = "application_gcm.xml";
    private static final String SOFBUS_24_FILES_GA_CLIENT_FILE = "gaClientId";
    private static final char[] SOFBUS_24_BACKUP_PASSWORD =
            "U09GQlVTXzI0X0JBQ0tVUF9QQVNTV09SRF9aRFJBVktPX05FU1RPUk9W".toCharArray();

    /**
     * Buffer size used while streaming bytes between {@link InputStream} /
     * {@link OutputStream} pairs.
     */
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    /**
     * Prefix used for any temporary file we stage in the application's cache
     * directory while performing import / export operations.
     */
    private static final String CACHE_FILE_PREFIX = "sofbus24_backup_";

    private final Context context;
    private final String sofbus24Path;
    private final String sofbus24PackageFolderName;

    public BackupData(Context context) {
        this.context = context.getApplicationContext();
        this.sofbus24Path = context.getFilesDir().getParent() + File.separator;
        this.sofbus24PackageFolderName = context.getPackageName();
    }

    // ---------------------------------------------------------------------
    //  Public API
    // ---------------------------------------------------------------------

    /**
     * Export the Sofbus 24 data to a Base64-encoded, AES-encrypted ZIP file.
     * The data contains all user data (favourites, settings and etc) except
     * the stations database.
     *
     * @param targetUri the SAF {@link Uri} where the encrypted/encoded backup
     *                  bytes will be written. Must be a writable URI obtained
     *                  through {@code ACTION_CREATE_DOCUMENT}.
     * @return {@code true} if the export was successful, {@code false} otherwise
     */
    public boolean exportSofbus24Data(Uri targetUri) {

        if (targetUri == null) {
            return false;
        }

        File stagingZipFile = null;
        try {
            // Stage the encrypted ZIP inside the application's private cache
            stagingZipFile = createCacheFile("export", ".zip");

            // Build the encrypted ZIP from the application's data folder
            createPasswordProtectedZipFile(sofbus24Path, stagingZipFile);

            // Base64-encode the ZIP and stream it into the user-selected URI
            writeBase64EncodedFileToUri(stagingZipFile, targetUri);

            return true;
        } catch (Exception e) {
            // Best-effort cleanup of the partially-written destination
            safeDeleteContent(targetUri);
            return false;
        } finally {
            // Always remove the staging file, regardless of the outcome
            deleteQuietly(stagingZipFile);
        }
    }

    /**
     * Import the Sofbus 24 data from a Base64-encoded, AES-encrypted ZIP file
     * previously produced by {@link #exportSofbus24Data(Uri)}.
     *
     * @param sourceUri the SAF {@link Uri} to read the encoded backup bytes
     *                  from. Must be a readable URI obtained through
     *                  {@code ACTION_OPEN_DOCUMENT}.
     * @return {@code true} if the import was successful, {@code false} otherwise
     */
    public boolean importSofbus24Data(Uri sourceUri) {

        if (sourceUri == null) {
            return false;
        }

        File stagingZipFile = null;
        File extractDirectory = null;

        try {
            // Stage both the decoded ZIP file and the extraction directory in cache
            stagingZipFile = createCacheFile("import", ".zip");
            extractDirectory = createCacheDirectory("extract");

            // Read the URI bytes, Base64-decode them and write the result to disk
            readBase64DecodedUriToFile(sourceUri, stagingZipFile);

            // Decrypt and unzip the staged ZIP into the staging directory
            unzipPasswordProtectedZipFile(stagingZipFile, extractDirectory.getAbsolutePath() + File.separator);

            // The ZIP contains a top-level folder named after the application
            // package. The original implementation used this folder as the source
            // of the copy step, so we keep the same convention here.
            File copySource = new File(extractDirectory, sofbus24PackageFolderName);
            String copySourcePath = (copySource.exists() && copySource.isDirectory())
                    ? copySource.getAbsolutePath() + File.separator
                    : extractDirectory.getAbsolutePath() + File.separator;

            // Copy the live database files into the staged directory so they are
            // preserved when we later replace the application's data folder.
            copyCurrentDb(copySourcePath, SOFBUS_24_DB_FILE);
            copyCurrentDb(copySourcePath, SOFBUS_24_DB_JOURNAL_FILE);

            // Replace the application's data folder with the staged content
            Utils.copyAndReplaceFileOrDirectory(copySourcePath, sofbus24Path);

            return true;
        } catch (Exception e) {
            return false;
        } finally {
            // Always remove the staging files, regardless of the outcome
            deleteQuietly(stagingZipFile);
            deleteQuietly(extractDirectory);
        }
    }

    // ---------------------------------------------------------------------
    //  Export helpers
    // ---------------------------------------------------------------------

    /**
     * Build an AES-encrypted ZIP file at {@code targetFile} that contains all
     * non-empty children of {@code sourceLocation} except the excluded files.
     *
     * @param sourceLocation absolute path to the application's data folder
     * @param targetFile     destination ZIP file (must be located on the local
     *                       file system, typically in the application's cache)
     * @throws Exception if no source data is found or any I/O / ZIP operation fails
     */
    private void createPasswordProtectedZipFile(String sourceLocation, File targetFile) throws Exception {

        // Make sure no leftover from a previous run is reused
        if (targetFile.exists() && !targetFile.delete()) {
            throw new IOException("Could not delete the previously staged ZIP file: "
                    + targetFile.getAbsolutePath());
        }

        // Create ZipParameters and configure them
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        // Create the ZIP file
        ZipFile zipFile = new ZipFile(targetFile);
        zipFile.setPassword(SOFBUS_24_BACKUP_PASSWORD);
        List<File> sourceLocationChildrenFiles = getChildrenFolders(sourceLocation);

        // This case should never been reached, because the source folder is created
        // when the application is installed (/data/data/bg.znestorov.sofbus24.main/)
        if (sourceLocationChildrenFiles.isEmpty()) {
            throw new IOException("The Sofbus 24 data folder doesn't exist. "
                    + "It seems that this is a major installation bug.");
        }

        // Add all not-empty folders located in the application data folder into the ZipFile
        for (File child : sourceLocationChildrenFiles) {
            zipFile.addFolder(child, zipParameters);
        }

        // Remove the stations database from the ZIP file
        removeZipEntryFile(zipFile, SOFBUS_24_DB_FILE);
        removeZipEntryFile(zipFile, SOFBUS_24_DB_JOURNAL_FILE);

        // Remove the GCM file, which contains the GCM ID and some other specific user information
        removeZipEntryFile(zipFile, SOFBUS_24_PREF_GCM_FILE);
        removeZipEntryFile(zipFile, SOFBUS_24_FILES_GA_CLIENT_FILE);
    }

    /**
     * Read the contents of {@code sourceFile}, Base64-encode them and stream
     * the result into the user-selected SAF location.
     *
     * @param sourceFile staged ZIP file produced during the export step
     * @param targetUri  user-selected SAF destination
     * @throws IOException if reading or writing fails
     */
    private void writeBase64EncodedFileToUri(File sourceFile, Uri targetUri) throws IOException {

        ContentResolver resolver = context.getContentResolver();

        // We must Base64-encode the entire ZIP at once to mirror the legacy
        // behaviour exactly (the old format used Base64.DEFAULT which adds
        // line breaks). To avoid OOM on huge files, the upstream UI restricts
        // backups to ~13 MiB.
        byte[] zipBytes = readAllBytes(sourceFile);
        byte[] encodedBytes = Base64.encode(zipBytes, Base64.DEFAULT);

        try (OutputStream out = resolver.openOutputStream(targetUri, "wt")) {
            if (out == null) {
                throw new IOException("ContentResolver returned a null OutputStream for " + targetUri);
            }
            out.write(encodedBytes);
            out.flush();
        }
    }

    /**
     * Get the children files located in the fileLocation. All files that don't exist
     * (system files) or can't be read are excluded.
     *
     * @param fileLocation the source file/directory location (will be zipped)
     * @return a list with all children files that can be copied
     */
    private List<File> getChildrenFolders(String fileLocation) {

        List<File> sofbus24DataFolders = new ArrayList<File>();

        // Check if the source location is not empty
        if (Utils.isEmpty(fileLocation)) {
            return sofbus24DataFolders;
        }

        // Check if the file from this source location exists
        File sourceFile = new File(fileLocation);
        if (!sourceFile.exists()) {
            return sofbus24DataFolders;
        }

        // Check if the source location contains any files or folders inside
        File[] sourceFileChildrenArray = sourceFile.listFiles();
        if (sourceFileChildrenArray == null || sourceFileChildrenArray.length == 0) {
            return sofbus24DataFolders;
        }

        // Iterate over all children of the source location and get only the existing ones, which are not empty.
        // It is possible that the array contains files that do not exists - they are system files.
        // For example: "/data/data/bg.znestorov.sofbus24.main/lib"
        for (File child : sourceFileChildrenArray) {
            File[] grandChildren = child.listFiles();
            if (child.exists() && child.canRead() && child.isDirectory()
                    && grandChildren != null && grandChildren.length > 0) {
                sofbus24DataFolders.add(child);
            }
        }

        return sofbus24DataFolders;
    }

    /**
     * Remove a ZipEntry from a ZipFile using its name.
     *
     * @param zipFile           the current zip file
     * @param zipFileHeaderName the name of the ZipEntry that will be deleted
     * @throws ZipException if the underlying ZIP operation fails
     */
    private void removeZipEntryFile(ZipFile zipFile, String zipFileHeaderName) throws ZipException {

        FileHeader zipFileHeader = null;
        List<FileHeader> zipFileHeaders = zipFile.getFileHeaders();

        // Look for the "zipFileHeaderName" (sofbus24.db or sofbus24.db-journal)
        for (int i = 0; i < zipFileHeaders.size(); i++) {
            FileHeader currentZipFileHeader = zipFileHeaders.get(i);
            String currentZipFileHeaderName = currentZipFileHeader.getFileName();

            // Check if the file header for "zipFileHeaderName" file is found
            if (currentZipFileHeaderName.endsWith(zipFileHeaderName)) {
                zipFileHeader = currentZipFileHeader;
                break;
            }
        }

        // Remove the "zipFileHeaderName" file from the ZIP file (sofbus24.db or sofbus24.db-journal)
        if (zipFileHeader != null) {
            zipFile.removeFile(zipFileHeader);
        }
    }

    // ---------------------------------------------------------------------
    //  Import helpers
    // ---------------------------------------------------------------------

    /**
     * Read all bytes available at {@code sourceUri}, Base64-decode them and
     * write the decoded payload to {@code destinationFile}.
     *
     * @param sourceUri       SAF source URI
     * @param destinationFile staged file to receive the decoded bytes
     * @throws IOException if reading or writing fails
     */
    private void readBase64DecodedUriToFile(Uri sourceUri, File destinationFile) throws IOException {

        ContentResolver resolver = context.getContentResolver();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = resolver.openInputStream(sourceUri)) {
            if (in == null) {
                throw new IOException("ContentResolver returned a null InputStream for " + sourceUri);
            }

            byte[] chunk = new byte[IO_BUFFER_SIZE];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        }

        byte[] decodedBytes = Base64.decode(buffer.toByteArray(), Base64.DEFAULT);

        try (OutputStream out = new FileOutputStream(destinationFile)) {
            out.write(decodedBytes);
            out.flush();
        }
    }

    /**
     * Unzip a password-protected ZIP file.
     *
     * @param zipFileToExtract the ZIP file to extract
     * @param targetLocation   the directory where the ZIP contents will be extracted
     * @throws ZipException if the underlying ZIP operation fails
     */
    private void unzipPasswordProtectedZipFile(File zipFileToExtract, String targetLocation) throws ZipException {

        ZipFile zipFile = new ZipFile(zipFileToExtract);

        // Check if the file was encrypted. If so - decrypt it using the password
        if (zipFile.isEncrypted()) {
            zipFile.setPassword(SOFBUS_24_BACKUP_PASSWORD);
        }

        // Extract the ZIP file
        zipFile.extractAll(targetLocation);
    }

    /**
     * Copy the current DB files to a given location.
     *
     * @param sourceLocation the source file/directory location
     * @param dbFilename     the name of the DB file
     * @throws IOException if the copy operation fails
     */
    private void copyCurrentDb(String sourceLocation, String dbFilename) throws IOException {

        String sofbus24SourceDbFilePath = sofbus24Path + SOFBUS_24_DB_FOLDER_NAME + File.separator + dbFilename;
        String sofbus24TargetDbFilePath = sourceLocation + SOFBUS_24_DB_FOLDER_NAME + File.separator + dbFilename;

        Utils.copyAndReplaceFileOrDirectory(sofbus24SourceDbFilePath, sofbus24TargetDbFilePath);
    }

    // ---------------------------------------------------------------------
    //  Generic I/O helpers
    // ---------------------------------------------------------------------

    /**
     * Read all bytes from a local {@link File} using a try-with-resources block.
     */
    private byte[] readAllBytes(File file) throws IOException {

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(file)) {
            byte[] chunk = new byte[IO_BUFFER_SIZE];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        }
        return buffer.toByteArray();
    }

    /**
     * Create a fresh empty file inside the application's private cache directory.
     */
    private File createCacheFile(String label, String suffix) throws IOException {

        File cacheDir = context.getCacheDir();
        if (cacheDir == null) {
            throw new IOException("The application cache directory is not available.");
        }

        File file = new File(cacheDir, CACHE_FILE_PREFIX + label + "_"
                + UUID.randomUUID().toString() + suffix);

        // Make sure no leftover from a previous run is reused
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete the previously staged file: "
                    + file.getAbsolutePath());
        }

        return file;
    }

    /**
     * Create a fresh empty directory inside the application's private cache directory.
     */
    private File createCacheDirectory(String label) throws IOException {

        File cacheDir = context.getCacheDir();
        if (cacheDir == null) {
            throw new IOException("The application cache directory is not available.");
        }

        File directory = new File(cacheDir, CACHE_FILE_PREFIX + label + "_"
                + UUID.randomUUID().toString());
        if (directory.exists()) {
            Utils.deleteFileOrDirectory(directory.getAbsolutePath());
        }

        if (!directory.mkdirs()) {
            throw new IOException("Could not create the staging cache directory: "
                    + directory.getAbsolutePath());
        }

        return directory;
    }

    /**
     * Delete a {@link File} (or directory) silently, ignoring any error.
     */
    private void deleteQuietly(File fileOrDirectory) {
        if (fileOrDirectory == null) {
            return;
        }
        try {
            Utils.deleteFileOrDirectory(fileOrDirectory.getAbsolutePath());
        } catch (Exception ignored) {
            // Ignored on purpose - cleanup must never throw
        }
    }

    /**
     * Best-effort attempt to truncate the user-selected SAF target. Used after
     * a failed export so that no half-written file is left behind.
     */
    private void safeDeleteContent(Uri targetUri) {
        if (targetUri == null) {
            return;
        }
        try (OutputStream out = context.getContentResolver().openOutputStream(targetUri, "wt")) {
            if (out != null) {
                out.flush();
            }
        } catch (Exception ignored) {
            // Ignored on purpose - cleanup must never throw
        }
    }
}
