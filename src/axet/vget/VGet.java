package axet.vget;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import axet.threads.LimitThreadPool;
import axet.vget.info.VGetParser;
import axet.vget.info.VideoFileInfo;
import axet.vget.info.VideoInfo;
import axet.vget.info.VideoInfo.States;
import axet.vget.vhs.VimeoParser;
import axet.vget.vhs.YouTubeParser;
import axet.wget.Direct;
import axet.wget.DirectMultipart;
import axet.wget.DirectRange;
import axet.wget.DirectSingle;
import axet.wget.RetryWrap;
import axet.wget.info.DownloadInfo;
import axet.wget.info.DownloadInfo.Part;
import axet.wget.info.ex.DownloadError;
import axet.wget.info.ex.DownloadIOCodeError;
import axet.wget.info.ex.DownloadIOError;
import axet.wget.info.ex.DownloadInterruptedError;
import axet.wget.info.ex.DownloadMultipartError;
import axet.wget.info.ex.DownloadRetry;

public class VGet {
    VideoInfo info;
    // target directory, where we have to download. automatically name files
    // based on video title and conflict files.
    File targetDir;

    // if target file exists, override it. ignores video titles and ignores
    // instead adding (1), (2) ... to filename suffix for conflict files
    // (exists files)
    File targetForce = null;

    /**
     * extract video information constructor
     * 
     * @param source
     *            url source to get video from
     */
    public VGet(URL source) {
        this(source, null);
    }

    public VGet(URL source, File targetDir) {
        this(parser(null, source).info(source), targetDir);
    }

    public VGet(VideoInfo info, File targetDir) {
        this.info = info;
        this.targetDir = targetDir;
    }

    public void setTarget(File file) {
        targetForce = file;
    }

    public void setTargetDir(File targetDir) {
        this.targetDir = targetDir;
    }

    public VideoInfo getVideo() {
        return info;
    }

    public void download() {
        download(null, new AtomicBoolean(false), new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    public void download(VGetParser user) {
        download(user, new AtomicBoolean(false), new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    /**
     * Drop all forbidden characters from filename
     * 
     * @param f
     *            input file name
     * @return normalized file name
     */
    static String replaceBadChars(String f) {
        String replace = " ";
        f = f.replaceAll("/", replace);
        f = f.replaceAll("\\\\", replace);
        f = f.replaceAll(":", replace);
        f = f.replaceAll("\\?", replace);
        f = f.replaceAll("\\\"", replace);
        f = f.replaceAll("\\*", replace);
        f = f.replaceAll("<", replace);
        f = f.replaceAll(">", replace);
        f = f.replaceAll("\\|", replace);
        f = f.trim();
        f = StringUtils.removeEnd(f, ".");
        f = f.trim();

        String ff;
        while (!(ff = f.replaceAll("  ", " ")).equals(f)) {
            f = ff;
        }

        return f;
    }

    static String maxFileNameLength(String str) {
        int max = 255;
        if (str.length() > max)
            str = str.substring(0, max);
        return str;
    }

    boolean done(AtomicBoolean stop) {
        if (stop.get())
            throw new DownloadInterruptedError("stop");
        if (Thread.currentThread().isInterrupted())
            throw new DownloadInterruptedError("interrupted");

        return false;
    }

    VideoFileInfo getNewInfo(List<VideoFileInfo> infoList, VideoFileInfo infoOld) {
        if (infoOld == null)
            return null;

        for (VideoFileInfo d : infoList) {
            if (infoOld.resume(d))
                return d;
        }

        return null;
    }

    void retry(VGetParser user, AtomicBoolean stop, Runnable notify, Throwable e) {
        boolean retracted = false;

        while (!retracted) {
            for (int i = RetryWrap.RETRY_DELAY; i >= 0; i--) {
                if (stop.get())
                    throw new DownloadInterruptedError("stop");
                if (Thread.currentThread().isInterrupted())
                    throw new DownloadInterruptedError("interrupted");

                info.setRetrying(i, e);
                notify.run();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ee) {
                    throw new DownloadInterruptedError(ee);
                }
            }

            try {
                // if we continue to download from old source, and this
                // proxy server is down we have to try to extract new info
                // and try to resume download

                List<VideoFileInfo> infoOldList = info.getInfo();

                user = parser(user, info.getWeb());
                user.info(info, stop, notify);

                // info replaced by user.info() call
                List<VideoFileInfo> infoNewList = info.getInfo();

                for (VideoFileInfo infoOld : infoOldList) {
                    DownloadInfo infoNew = getNewInfo(infoNewList, infoOld);

                    if (infoOld != null && infoNew != null && infoOld.resume(infoNew)) {
                        infoNew.copy(infoOld);
                    } else {
                        mergeExt(infoOld);
                        if (infoOld.targetFile != null) {
                            FileUtils.deleteQuietly(infoOld.targetFile);
                            infoOld.targetFile = null;
                        }
                    }

                    retracted = true;
                }
            } catch (DownloadIOCodeError ee) {
                if (retry(ee)) {
                    info.setState(States.RETRYING, ee);
                    notify.run();
                } else {
                    throw ee;
                }
            } catch (DownloadRetry ee) {
                info.setState(States.RETRYING, ee);
                notify.run();
            }
        }
    }

    String getExt(DownloadInfo dinfo) {
        String ct = dinfo.getContentType();
        if (ct == null)
            throw new DownloadRetry("null content type");

        ct = ct.replaceFirst("video/", "");

        ct = ct.replaceFirst("audio/", "");

        return "." + ct.replaceAll("x-", "").toLowerCase();
    }

    void targetFile(VideoFileInfo dinfo) {
        if (targetForce != null) {
            dinfo.targetFile = targetForce;

            if (dinfo.multipart()) {
                if (!DirectMultipart.canResume(dinfo, dinfo.targetFile))
                    dinfo.targetFile = null;
            } else if (dinfo.getRange()) {
                if (!DirectRange.canResume(dinfo, dinfo.targetFile))
                    dinfo.targetFile = null;
            } else {
                if (!DirectSingle.canResume(dinfo, dinfo.targetFile))
                    dinfo.targetFile = null;
            }
        }

        if (dinfo.targetFile == null) {
            if (targetDir == null) {
                throw new RuntimeException("Set download file or directory first");
            }

            File f;

            Integer idupcount = 0;

            String sfilename = replaceBadChars(info.getTitle());

            sfilename = maxFileNameLength(sfilename);

            String ext = getExt(dinfo);

            do {
                String add = idupcount > 0 ? " (".concat(idupcount.toString()).concat(")") : "";

                f = new File(targetDir, sfilename + add + ext);
                idupcount += 1;
            } while (f.exists());

            dinfo.targetFile = f;

            // if we dont have resume file (targetForce==null) then we shall
            // start over.
            dinfo.reset();
        }
    }

    boolean retry(Throwable e) {
        if (e == null)
            return true;

        if (e instanceof DownloadIOCodeError) {
            DownloadIOCodeError c = (DownloadIOCodeError) e;
            switch (c.getCode()) {
            case HttpURLConnection.HTTP_FORBIDDEN:
            case 416: // 416 Requested Range Not Satisfiable
                return true;
            default:
                return false;
            }
        }

        return false;
    }

    /**
     * @return return status of download information. subclassing for
     *         VideoInfo.empty();
     * 
     */
    public boolean empty() {
        return getVideo().empty();
    }

    public void extract() {
        extract(new AtomicBoolean(false), new Runnable() {
            @Override
            public void run() {
            }
        });
    }

    public void extract(AtomicBoolean stop, Runnable notify) {
        extract(null, stop, notify);
    }

    /**
     * extract video information, retry until success
     * 
     * @param user
     *            user info object
     * @param stop
     *            stop signal boolean
     * @param notify
     *            notify executre
     */
    public void extract(VGetParser user, AtomicBoolean stop, Runnable notify) {
        while (!done(stop)) {
            try {
                if (info.empty()) {
                    info.setState(States.EXTRACTING);
                    user = parser(user, info.getWeb());
                    user.info(info, stop, notify);
                    info.setState(States.EXTRACTING_DONE);
                    notify.run();
                }
                return;
            } catch (DownloadRetry e) {
                retry(user, stop, notify, e);
            } catch (DownloadMultipartError e) {
                checkFileNotFound(e);
                checkRetry(e);
                retry(user, stop, notify, e);
            } catch (DownloadIOCodeError e) {
                if (retry(e))
                    retry(user, stop, notify, e);
                else
                    throw e;
            } catch (DownloadIOError e) {
                retry(user, stop, notify, e);
            }
        }
    }

    void checkRetry(DownloadMultipartError e) {
        for (Part ee : e.getInfo().getParts()) {
            if (!retry(ee.getException())) {
                throw e;
            }
        }
    }

    /**
     * check if all parts has the same filenotfound exception. if so throw
     * DownloadError.FilenotFoundexcepiton
     * 
     * @param e
     *            error occured
     */
    void checkFileNotFound(DownloadMultipartError e) {
        FileNotFoundException f = null;
        for (Part ee : e.getInfo().getParts()) {
            // no error for this part? skip it
            if (ee.getException() == null)
                continue;
            // this exception has no cause? then it is not FileNotFound
            // excpetion. then do noting. this is checking function. do not
            // rethrow
            if (ee.getException().getCause() == null)
                return;
            if (ee.getException().getCause() instanceof FileNotFoundException) {
                // our first filenotfoundexception?
                if (f == null) {
                    // save it for later checks
                    f = (FileNotFoundException) ee.getException().getCause();
                } else {
                    // check filenotfound error message is it the same?
                    FileNotFoundException ff = (FileNotFoundException) ee.getException().getCause();
                    if (!ff.getMessage().equals(f.getMessage())) {
                        // if the filenotfound exception message is not the
                        // same. then we cannot retrhow filenotfound exception.
                        // return and continue checks
                        return;
                    }
                }
            } else {
                break;
            }
        }
        if (f != null)
            throw new DownloadError(f);
    }

    public void download(final AtomicBoolean stop, final Runnable notify) {
        download(null, stop, notify);
    }

    void mergeExt(VideoFileInfo info) {
        if (info.targetFile == null)
            return;

        String ext = getExt(info);

        String f = info.targetFile.getAbsolutePath();
        if (f.toLowerCase().endsWith(ext.toLowerCase())) {
            info.targetFile = new File(f);
        }
        f = FilenameUtils.removeExtension(f);
        info.targetFile = new File(f + ext);
    }

    public void download(VGetParser user, final AtomicBoolean stop, final Runnable notify) {
        try {
            if (empty()) {
                extract(user, stop, notify);
            }

            while (!done(stop)) {
                try {
                    final List<VideoFileInfo> dinfoList = info.getInfo();

                    // all working threads have its own stop. separated from
                    // axet.vget.stop it is necessary because we have to be able to
                    // cancel downloading for a single DownloadInfo without
                    // stopping whole download.
                    final AtomicBoolean stopl = new AtomicBoolean(false);

                    Thread stopr = new Thread("stopr") {
                        @Override
                        public void run() {
                            synchronized (stop) {
                                try {
                                    stop.wait();
                                } catch (InterruptedException e) {
                                    return;
                                }
                                stopl.set(stop.get());
                            }
                        }
                    };
                    stopr.start();

                    LimitThreadPool l = new LimitThreadPool(4);

                    for (final VideoFileInfo dinfo : dinfoList) {
                        {
                            boolean v = dinfo.getContentType().contains("video/");
                            boolean a = dinfo.getContentType().contains("audio/");
                            if (dinfo.getContentType() == null || (!v && !a)) {
                                stopl.set(true);
                                throw new DownloadRetry(
                                        "unable to download video, bad content " + dinfo.getContentType());
                            }
                        }

                        targetFile(dinfo);

                        mergeExt(dinfo);

                        if (dinfo.targetFile == null) {
                            throw new RuntimeException("bad target");
                        }

                        Direct directV;

                        if (dinfo.multipart()) {
                            // multi part? overwrite.
                            directV = new DirectMultipart(dinfo, dinfo.targetFile);
                        } else if (dinfo.getRange()) {
                            // range download? try to resume download from last
                            // position
                            if (dinfo.targetFile.exists() && dinfo.targetFile.length() != dinfo.getCount())
                                dinfo.targetFile = null;
                            directV = new DirectRange(dinfo, dinfo.targetFile);
                        } else {
                            // single download? overwrite file
                            directV = new DirectSingle(dinfo, dinfo.targetFile);
                        }
                        final Direct direct = directV;

                        final Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                switch (dinfo.getState()) {
                                case DOWNLOADING:
                                    info.setState(States.DOWNLOADING);
                                    notify.run();
                                    break;
                                case RETRYING:
                                    info.setRetrying(dinfo.getDelay(), dinfo.getException());
                                    notify.run();
                                    break;
                                default:
                                    // we can safely skip all statues.
                                    // (extracting - already passed, STOP /
                                    // ERROR / DONE i will catch up here
                                }
                            }
                        };

                        try {
                            l.blockExecute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        direct.download(stopl, r);
                                    } catch (DownloadInterruptedError e) {
                                        synchronized (stopl) {
                                            stopl.set(true);
                                            stopl.notifyAll();
                                        }
                                    }
                                }
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    try {
                        l.waitUntilTermination();
                        stopr.interrupt();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    info.setState(States.DONE);
                    notify.run();

                    // break while()
                    return;
                } catch (DownloadRetry e) {
                    retry(user, stop, notify, e);
                } catch (DownloadMultipartError e) {
                    checkFileNotFound(e);
                    checkRetry(e);
                    retry(user, stop, notify, e);
                } catch (DownloadIOCodeError e) {
                    if (retry(e))
                        retry(user, stop, notify, e);
                    else
                        throw e;
                } catch (DownloadIOError e) {
                    retry(user, stop, notify, e);
                }
            }
        } catch (DownloadInterruptedError e) {
            info.setState(VideoInfo.States.STOP, e);
            notify.run();

            throw e;
        } catch (RuntimeException e) {
            info.setState(VideoInfo.States.ERROR, e);
            notify.run();

            throw e;
        }
    }

    public static VGetParser parser(URL web) {
        return parser(null, web);
    }

    public static VGetParser parser(VGetParser user, URL web) {
        VGetParser ei = user;

        if (ei == null && YouTubeParser.probe(web))
            ei = new YouTubeParser();

        if (ei == null && VimeoParser.probe(web))
            ei = new VimeoParser();

        if (ei == null)
            throw new RuntimeException("unsupported web site");

        return ei;
    }

}
