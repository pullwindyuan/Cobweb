package com.xiongbeer.cobweb.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by shaoxiong on 17-5-10.
 */
public class MD5MakerTest {
    @Test
    public void hashTest() {
        String key = "cobweb";
        String value = "0366e5f33683e855ae4894c6518a5a7e";
        MD5Maker md5Maker = new MD5Maker();
        md5Maker.update(key);
        assertEquals(value, md5Maker.toString());
    }
}
