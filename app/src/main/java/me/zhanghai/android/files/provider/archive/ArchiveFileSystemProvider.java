/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.archive;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java8.nio.channels.FileChannel;
import java8.nio.channels.SeekableByteChannel;
import java8.nio.file.AccessDeniedException;
import java8.nio.file.AccessMode;
import java8.nio.file.CopyOption;
import java8.nio.file.DirectoryStream;
import java8.nio.file.FileStore;
import java8.nio.file.FileSystem;
import java8.nio.file.FileSystemAlreadyExistsException;
import java8.nio.file.FileSystemNotFoundException;
import java8.nio.file.Files;
import java8.nio.file.LinkOption;
import java8.nio.file.OpenOption;
import java8.nio.file.Path;
import java8.nio.file.Paths;
import java8.nio.file.ProviderMismatchException;
import java8.nio.file.ReadOnlyFileSystemException;
import java8.nio.file.attribute.BasicFileAttributes;
import java8.nio.file.attribute.FileAttribute;
import java8.nio.file.attribute.FileAttributeView;
import java8.nio.file.spi.FileSystemProvider;
import me.zhanghai.android.files.provider.common.AccessModes;
import me.zhanghai.android.files.provider.common.OpenOptions;
import me.zhanghai.android.files.provider.common.StringPath;
import me.zhanghai.android.files.util.MapCompat;

public class ArchiveFileSystemProvider extends FileSystemProvider {

    private static final String SCHEME = "file";

    @NonNull
    private final Map<Path, ArchiveFileSystem> mFileSystems = new HashMap<>();
    @NonNull
    private final Object mFileSystemsLock = new Object();

    private ArchiveFileSystemProvider() {}

    public static void install() {
        FileSystemProvider.installProvider(new ArchiveFileSystemProvider());
    }

    @NonNull
    @Override
    public String getScheme() {
        return SCHEME;
    }

    @NonNull
    @Override
    public FileSystem newFileSystem(@NonNull URI uri, @NonNull Map<String, ?> env)
            throws IOException {
        Objects.requireNonNull(uri);
        requireSameScheme(uri);
        Objects.requireNonNull(env);
        Path archiveFile = getArchiveFileFromUri(uri);
        ArchiveFileSystem fileSystem;
        synchronized (mFileSystemsLock) {
            if (mFileSystems.containsKey(archiveFile)) {
                throw new FileSystemAlreadyExistsException();
            }
            fileSystem = newFileSystem(archiveFile);
            mFileSystems.put(archiveFile, fileSystem);
        }
        return fileSystem;
    }

    @NonNull
    @Override
    public FileSystem getFileSystem(@NonNull URI uri) {
        Objects.requireNonNull(uri);
        requireSameScheme(uri);
        Path archiveFile = getArchiveFileFromUri(uri);
        ArchiveFileSystem fileSystem;
        synchronized (mFileSystemsLock) {
            fileSystem = mFileSystems.get(archiveFile);
        }
        if (fileSystem == null) {
            throw new FileSystemNotFoundException();
        }
        return fileSystem;
    }

    @NonNull
    private static Path getArchiveFileFromUri(@NonNull URI uri) {
        URI archiveUri;
        try {
            archiveUri = new URI(uri.getSchemeSpecificPart());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        return Paths.get(archiveUri);
    }

    void removeFileSystem(@NonNull ArchiveFileSystem fileSystem) {
        synchronized (mFileSystemsLock) {
            MapCompat.remove(mFileSystems, fileSystem.getArchiveFile(), fileSystem);
        }
    }

    @NonNull
    @Override
    public Path getPath(@NonNull URI uri) {
        Objects.requireNonNull(uri);
        requireSameScheme(uri);
        return getFileSystem(uri).getPath(uri.getFragment());
    }

    private static void requireSameScheme(@NonNull URI uri) {
        if (!Objects.equals(uri.getScheme(), SCHEME)) {
            throw new IllegalArgumentException("URI scheme must be \"" + SCHEME + "\"");
        }
    }

    @NonNull
    @Override
    public FileSystem newFileSystem(@NonNull Path file, @NonNull Map<String, ?> env)
            throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(env);
        return newFileSystem(file);
    }

    @NonNull
    private ArchiveFileSystem newFileSystem(@NonNull Path file) throws IOException {
        return new ArchiveFileSystem(this, file);
    }

    @NonNull
    @Override
    public InputStream newInputStream(@NonNull Path file, @NonNull OpenOption... options)
            throws IOException {
        requireArchivePath(file);
        Objects.requireNonNull(options);
        OpenOptions openOptions = OpenOptions.fromArray(options);
        ArchiveOpenOptions.check(openOptions);
        ArchiveFileSystem fileSystem = (ArchiveFileSystem) file.getFileSystem();
        return fileSystem.newInputStream(file);
    }

    @NonNull
    @Override
    public FileChannel newFileChannel(@NonNull Path file,
                                      @NonNull Set<? extends OpenOption> options,
                                      @NonNull FileAttribute<?>... attributes) {
        requireArchivePath(file);
        Objects.requireNonNull(options);
        Objects.requireNonNull(attributes);
        OpenOptions openOptions = OpenOptions.fromSet(options);
        ArchiveOpenOptions.check(openOptions);
        if (attributes.length > 0) {
            throw new UnsupportedOperationException(Arrays.toString(attributes));
        }
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public SeekableByteChannel newByteChannel(@NonNull Path file,
                                              @NonNull Set<? extends OpenOption> options,
                                              @NonNull FileAttribute<?>... attributes) {
        requireArchivePath(file);
        Objects.requireNonNull(options);
        Objects.requireNonNull(attributes);
        OpenOptions openOptions = OpenOptions.fromSet(options);
        ArchiveOpenOptions.check(openOptions);
        if (attributes.length > 0) {
            throw new UnsupportedOperationException(Arrays.toString(attributes));
        }
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public DirectoryStream<Path> newDirectoryStream(
            @NonNull Path directory, @NonNull DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        requireArchivePath(directory);
        Objects.requireNonNull(filter);
        ArchiveFileSystem fileSystem = (ArchiveFileSystem) directory.getFileSystem();
        List<Path> children = fileSystem.getChildren(directory);
        return new ArchiveDirectoryStream(children, filter);
    }

    @Override
    public void createDirectory(@NonNull Path directory, @NonNull FileAttribute<?>... attributes) {
        requireArchivePath(directory);
        Objects.requireNonNull(attributes);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void createSymbolicLink(@NonNull Path link, @NonNull Path target,
                                   @NonNull FileAttribute<?>... attrs) {
        requireArchivePath(link);
        requireArchivePath(target);
        Objects.requireNonNull(attrs);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void createLink(@NonNull Path link, @NonNull Path existing) {
        requireArchivePath(link);
        requireArchivePath(existing);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void delete(@NonNull Path path) {
        requireArchivePath(path);
        throw new ReadOnlyFileSystemException();
    }

    @NonNull
    @Override
    public Path readSymbolicLink(@NonNull Path link) throws IOException {
        requireArchivePath(link);
        ArchiveFileSystem fileSystem = (ArchiveFileSystem) link.getFileSystem();
        String target = fileSystem.readSymbolicLink(link);
        return new StringPath(target);
    }

    @Override
    public void copy(@NonNull Path source, @NonNull Path target, @NonNull CopyOption... options) {
        requireArchivePath(source);
        requireArchivePath(target);
        Objects.requireNonNull(options);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void move(@NonNull Path source, @NonNull Path target, @NonNull CopyOption... options) {
        requireArchivePath(source);
        requireArchivePath(target);
        Objects.requireNonNull(options);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public boolean isSameFile(@NonNull Path path, @NonNull Path path2) throws IOException {
        requireArchivePath(path);
        Objects.requireNonNull(path2);
        if (Objects.equals(path, path2)) {
            return true;
        }
        if (!(path instanceof ArchivePath)) {
            return false;
        }
        ArchiveFileSystem fileSystem = (ArchiveFileSystem) path.getFileSystem();
        ArchiveFileSystem fileSystem2 = (ArchiveFileSystem) path2.getFileSystem();
        if (!Files.isSameFile(fileSystem.getArchiveFile(), fileSystem2.getArchiveFile())) {
            return false;
        }
        return Objects.equals(path, fileSystem.getPath(path2.toString()));
    }

    @Override
    public boolean isHidden(@NonNull Path path) {
        requireArchivePath(path);
        return false;
    }

    @NonNull
    @Override
    public FileStore getFileStore(@NonNull Path path) {
        requireArchivePath(path);
        ArchiveFileSystem fileSystem = (ArchiveFileSystem) path.getFileSystem();
        Path archiveFile = fileSystem.getArchiveFile();
        return new ArchiveFileStore(archiveFile);
    }

    @Override
    public void checkAccess(@NonNull Path path, @NonNull AccessMode... modes) throws IOException {
        requireArchivePath(path);
        Objects.requireNonNull(modes);
        AccessModes accessModes = AccessModes.fromArray(modes);
        ArchiveFileSystem fileSystem = (ArchiveFileSystem) path.getFileSystem();
        fileSystem.getEntry(path);
        if (accessModes.hasWrite() || accessModes.hasExecute()) {
            throw new AccessDeniedException(path.toString());
        }
    }

    @Nullable
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(@NonNull Path path,
                                                                @NonNull Class<V> type,
                                                                @NonNull LinkOption... options) {
        requireArchivePath(path);
        Objects.requireNonNull(type);
        Objects.requireNonNull(options);
        if (!supportsFileAttributeView(type)) {
            return null;
        }
        //noinspection unchecked
        return (V) getFileAttributeView(path);
    }

    @NonNull
    @Override
    public <A extends BasicFileAttributes> A readAttributes(@NonNull Path path,
                                                            @NonNull Class<A> type,
                                                            @NonNull LinkOption... options)
            throws IOException {
        requireArchivePath(path);
        Objects.requireNonNull(type);
        Objects.requireNonNull(options);
        if (!type.isAssignableFrom(ArchiveFileAttributes.class)) {
            throw new UnsupportedOperationException(type.toString());
        }
        //noinspection unchecked
        return (A) getFileAttributeView(path).readAttributes();
    }

    static boolean supportsFileAttributeView(@NonNull Class<? extends FileAttributeView> type) {
        return type.isAssignableFrom(ArchiveFileAttributeView.class);
    }

    private static ArchiveFileAttributeView getFileAttributeView(@NonNull Path path) {
        return new ArchiveFileAttributeView(path);
    }

    @NonNull
    @Override
    public Map<String, Object> readAttributes(@NonNull Path path, @NonNull String attributes,
                                              @NonNull LinkOption... options) {
        requireArchivePath(path);
        Objects.requireNonNull(attributes);
        Objects.requireNonNull(options);
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(@NonNull Path path, @NonNull String attribute, @NonNull Object value,
                             @NonNull LinkOption... options) {
        requireArchivePath(path);
        Objects.requireNonNull(attribute);
        Objects.requireNonNull(value);
        Objects.requireNonNull(options);
        throw new UnsupportedOperationException();
    }

    private static void requireArchivePath(@NonNull Path path) {
        Objects.requireNonNull(path);
        if (!(path instanceof ArchivePath)) {
            throw new ProviderMismatchException(path.toString());
        }
    }
}
