package com.xiongbeer.cobweb.filter;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.xiongbeer.cobweb.conf.StaticField;
import com.xiongbeer.cobweb.exception.FilterException;
import com.xiongbeer.cobweb.utils.MD5Maker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by shaoxiong on 17-5-10.
 */
public class URIBloomFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(URIBloomFilter.class);

    private BloomFilter<CharSequence> bloomFilter;

    private AtomicLong urlCounter;

    private double fpp;

    private long expectedInsertions;

    private String savePath;

    private BloomFileInfo bloomFileInfo;

    /**
     * 初始化一个bloom过滤器到内存中
     *
     * @param expectedInsertions 预估的最大元素容量
     * @param fpp                误报概率
     */
    public URIBloomFilter(long expectedInsertions, double fpp) {
        this.bloomFileInfo = new BloomFileInfo(0, expectedInsertions, fpp);
        this.urlCounter = new AtomicLong(0);
        this.expectedInsertions = expectedInsertions;
        this.fpp = fpp;
        this.bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(Charset.defaultCharset()), expectedInsertions, fpp);
    }

    /**
     * 读取持久化的bloom缓存文件来初始化
     * <p>
     * 默认当前只存在一个bloom缓存文件
     * 将其读取到内存中来
     *
     * @param cacheDir 存取缓存文件的文件夹路径
     * @throws IOException
     */
    @Deprecated
    public URIBloomFilter(String cacheDir) throws IOException, FilterException.IllegalFilterCacheNameException {
        File dir = new File(cacheDir);
        File file = new File(getBloomFileName(dir));
        BloomFileInfo info = new BloomFileInfo(file.getName());
        urlCounter = new AtomicLong(info.getUrlCounter());
        expectedInsertions = info.getExpectedInsertions();
        fpp = info.getFpp();
        load(file.getAbsolutePath());
    }

    /**
     * 读取持久化的bloom缓存文件来初始化
     * <p>
     * 读取某个特定的名字的bloom缓存文件
     *
     * @param cacheDir          存取缓存文件的文件夹路径
     * @param uniqueMarkupRegex 能捕获包含唯一标识符的bloom缓存文件的正则表达式
     * @throws IOException
     */
    public URIBloomFilter(String cacheDir, int uniqueMarkupRegex)
            throws IOException, FilterException {
        File dir = new File(cacheDir);
        String bloomFileName = getBloomFileName(dir, uniqueMarkupRegex);
        File file = new File(cacheDir + File.separator + bloomFileName);
        this.bloomFileInfo = new BloomFileInfo(bloomFileName);
        this.urlCounter = new AtomicLong(bloomFileInfo.getUrlCounter());
        this.expectedInsertions = bloomFileInfo.getExpectedInsertions();
        this.fpp = bloomFileInfo.getFpp();
        load(file.getAbsolutePath());
    }

    /**
     * 获取bloom缓存文件名
     * <p>
     * 获取规则：以Configuration中预先设置的BLOOM_CACHE_FILE_SUFFIX结尾
     *
     * @param dir 读取的文件夹
     * @return 唯一的bloom缓存文件名字
     * @throws IOException 当读取的路径下没有或者有多于一个
     *                     bloom缓存文件的时候就会抛出异常
     */
    @Deprecated
    public static String getBloomFileName(File dir) throws IOException {
        File[] files = dir.listFiles(pathname -> {
            if (pathname.toString().endsWith(
                    StaticField.BLOOM_CACHE_FILE_SUFFIX)) {
                return true;
            }
            return false;
        });
        if (files.length == 0) {
            throw new IOException("No bloom cache file exist.");
        }
        return files[0].getAbsolutePath();
    }

    /**
     * 获取bloom缓存文件名
     * <p>
     * 获取规则：用户自己定义的唯一标识符的正则表达式
     * tip: 用户应该提前用Java自带的正则库写个小程序
     * 来测试自己定义的规则是否能正常获取
     *
     * @param dir
     * @param uniqueMarkupRegex
     * @return 唯一的bloom缓存文件名字
     * @throws IOException 没有获取到或者获取到多个本该是唯一
     *                     bloom缓存文件的时候就会抛出异常
     */
    public static String getBloomFileName(File dir, final int uniqueMarkupRegex) throws FilterException {
        File[] files = dir.listFiles(pathname -> {
            BloomFileInfo info;
            try {
                info = new BloomFileInfo(pathname.getName());
            } catch (FilterException.IllegalFilterCacheNameException e) {
                /* ignore illegal file is ok */
                return false;
            } catch (NumberFormatException e) {
                logger.warn("A filter cache file might be broken : " + pathname);
                return false;
            }
            return info.getMarkup() == uniqueMarkupRegex;
        });
        if (files.length > 1) {
            throw new FilterException("Duplicate unique bloom files, uniqueMarkup: "
                    + uniqueMarkupRegex);
        } else if (files.length == 0) {
            throw new FilterException("No such unique bloom cache file, uniqueMarkup:"
                    + uniqueMarkupRegex);
        }
        return files[0].getAbsolutePath();
    }

    /**
     * 持久化bloom过滤器在内存中的状态
     * Guava中BloomFilter序列化格式：
     * +-----------------------------------------------------------------------+
     * | strategyOrdinal | numHashFunctions | dataLength |      BitArray       |
     * |     1 byte      |      1 byte      |    1 int   |  8*dataLength bytes |
     * +-----------------------------------------------------------------------+
     * 采用的numHashFunctions个Hash函数实现：
     * 首先使用murmurHash3对Object生成一个128位的值，取低64位值为combinedHash
     * 1.mask combinedHash的符号位后对BitArray.Size进行取余后的值就是新的置true的位置
     * 2.combinedHash更新为combinedHash += 高64位的值
     * 按上2步骤循环numHashFunctions次
     *
     * @param targetDir 存储的文件夹
     * @return 保存文件的绝对路径
     * @throws IOException
     */
    public String save(String targetDir) throws IOException {
        savePath = targetDir + File.separator + bloomFileInfo.toString()
                + StaticField.TEMP_SUFFIX;
        File file = new File(savePath);
        FileOutputStream fos = new FileOutputStream(file);
        bloomFilter.writeTo(fos);
        return file.getAbsolutePath();
    }

    public void delete() {
        Optional.ofNullable(savePath).ifPresent(val -> new File(val).delete());
    }

    @Override
    public void load(String path) throws IOException {
        File file = new File(path);
        FileInputStream fis = new FileInputStream(file);
        bloomFilter = BloomFilter.readFrom(fis, Funnels.stringFunnel(Charset.defaultCharset()));
    }

    @Override
    public int getMarkup() {
        return bloomFileInfo.getMarkup();
    }

    /**
     * 将某个url存入bloom过滤器中
     * <p>
     * 这里使用对url取MD5的原因是这样可以去贴近理论的fpp值
     * 直接裸用url的话，很可能导致实际fpp值比预设值的fpp更高
     *
     * @param url
     */
    @Override
    public boolean put(String url) {
        boolean flag;
        MD5Maker md5 = new MD5Maker(url);
        String md5Value = md5.toString();
        synchronized (this) {
            flag = bloomFilter.put(md5Value);
        }
        if (flag) {
            bloomFileInfo.setUrlCounter(urlCounter.incrementAndGet());
        }
        return flag;
    }

    @Override
    public boolean exist(String str) {
        return mightContain(str);
    }

    /**
     * 判断是否可能包含某个url
     *
     * @param url
     * @return true url可能已经存在
     * false url一定不存在
     */
    public boolean mightContain(String url) {
        MD5Maker md5 = new MD5Maker(url);
        return bloomFilter.mightContain(md5.toString());
    }

    public long getUrlCounter() {
        return urlCounter.longValue();
    }

    public double getFpp() {
        return fpp;
    }

    public long getExpectedInsertions() {
        return expectedInsertions;
    }
}