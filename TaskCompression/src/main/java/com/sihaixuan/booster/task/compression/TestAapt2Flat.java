package com.sihaixuan.booster.task.compression;

import com.didiglobal.booster.aapt2.BinaryParser;

import java.io.File;
import java.nio.ByteOrder;

/**
 * 项目名称：BoosterDemo
 * 类描述：
 * 创建人：toney
 * 创建时间：2019/6/3 16:00
 * 邮箱：xiyangfeisa@foxmail.com
 * 备注：
 *
 * @version 1.0
 */
public class TestAapt2Flat {
    public static void main(String[] args){
        File path = new File("F:\\developer_workspace\\android__workspace\\NewStudy\\BoosterDemo\\app\\build\\intermediates\\res\\merged\\debug");

        for(File file : path.listFiles()){
            if(file.isFile()){
                BinaryParser parser = new BinaryParser(file, ByteOrder.LITTLE_ENDIAN);
                StringBuffer buffer = new StringBuffer();
                buffer.append(file.getName()).append(" ");
                buffer.append("magic = ").append(Integer.toHexString(parser.readInt())).append(" , ");
                buffer.append("version = ").append(parser.readInt()).append(" , ");
                buffer.append("count = ").append(parser.readInt()).append(" , ");
                parser.tell();
                int type = parser.readInt();
                buffer.append("type = ").append(type).append(" , ");
                buffer.append("length = ").append(parser.readLong()).append(" , ");

                if(type == 1){
                    int headerSize = parser.readInt();
                    long dataSize = parser.readLong();
                    buffer.append("headerSize = ").append(headerSize).append(" , ");
                    buffer.append("dataSize = ").append(dataSize).append(" , ");
                }

                System.out.println(buffer.toString());
            }
        }


    }
}
