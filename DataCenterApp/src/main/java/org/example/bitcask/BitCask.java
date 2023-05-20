package org.example.bitcask;

import java.io.*;
import java.util.HashMap;
import java.io.FileInputStream;
import static org.example.bitcask.Paths.*;
record activeHashedValue(String filePath, long offset) {
}
public class BitCask {
    /* Important notes:
       When the system goes down?
       these values will reset, but they shouldn't,
       so we need to get the right values for them
       RECOVERY
    */
    static HashMap<Long, activeHashedValue> hashTable = new HashMap<>();
    // To keep track of Non compacted Files
    static int NumberOf_Non_CompactedFiles = 0;
    static int LastNonCompacted = 0;
    // To keep track of compacted files
    static int NumberOfCompactedFiles = 0;
    private final static int maxFileSize= 1000;
    private final static int FilesNumberLimit = 5;
    public static final int NumberOfKeys = 10;
    private static final Compact compactObj = new Compact();
    boolean firstWrite = true;

    public BitCask() {
    }
    private void createNewFile(){
        LastNonCompacted++;
        NumberOf_Non_CompactedFiles++;
    }

    private void put(Record record, String filepath) {
        long key = record.station_id;
        File file = new File(filepath);
        long offset = file.length();
        if(file.exists()){
            firstWrite = false;
        }else{
            firstWrite = true;
        }
        System.out.println(offset);
        hashTable.put(key, new activeHashedValue(filepath,offset));
        this.writeObj(record, filepath);
        if (file.length() >= maxFileSize){
            createNewFile();
        }
    }

    private void writeObj(Record record, String filepath){
        try {
            FileOutputStream fo = new FileOutputStream(filepath, true);
            DataOutputStream os = new DataOutputStream(fo);
            os.writeLong(record.station_id);
            os.writeLong(record.s_no);
            os.writeUTF(record.battery_status);
            os.writeLong(record.status_timestamp);
            os.writeInt(record.weather.humidity);
            os.writeInt(record.weather.temperature);
            os.writeInt(record.weather.wind_speed);
            os.close();
            fo.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    public Record get(long key) {
        long offset = hashTable.get(key).offset();
        String filepath = hashTable.get(key).filePath();
        Record record = null;
        File file = new File(filepath);
        if (file.exists()) {
            try {
                FileInputStream fi = new FileInputStream(filepath);
                fi.skip(offset);
                DataInputStream is = new DataInputStream(fi);
                record = new Record();
                record.station_id = is.readLong();
                record.s_no = is.readLong();
                record.battery_status = is.readUTF();
                record.status_timestamp = is.readLong();
                Weather w = new Weather();
                w.humidity = is.readInt();
                w.temperature = is.readInt();
                w.wind_speed = is.readInt();
                record.weather = w;
                is.close();
                fi.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            System.out.println("Get: File doesn't exist");
        }

        return record;
    }

    /*
        background compaction process is spun off which merges
        ((all non-active immutable files)) and produces output files with the latest
        modifications for all the keys. The compaction process also modifies the in-memory KeyDir
        with information about merged files. Now that this compaction is performed
        periodically, we can ensure that the recovery process won’t get bogged down
        due to large number of non-active files, and we will be able to reboot the database
        server in an efficient manner.
     */

    // last file number could be modified by another thread
    // this is why it's passed here would this help??
    private void compact(String CurrentDir, int NumberOfCompactedFiles, int LastNonCompactedNumber) {

//        //////////////////
//        ///This should be done carefully
          // I think old files shouldn't be collected immediately,
          // After compaction this should be done safely;
          // collect_old_files(currentDir);
         /*
            Hint files for compacted files should be deleted
         */

         compactObj.compact(CurrentDir, NumberOfCompactedFiles);
         HashMap<Long, compactedHashedValue> compactedHashMap = compactObj.compactedHashMap;
         String compactionPath = getCompactionPath(CurrentDir, NumberOfCompactedFiles);
        /*
            Updating the active hashmap this should be done carefully
            This code must go somewhere else
            I think here we must stop other readers and writers to
            Modify the active hashmap
        */
        for (long key : compactedHashMap.keySet()) {
            compactedHashedValue value = compactedHashMap.get(key);
            System.out.println(hashTable.get(key).filePath() + "  " + hashTable.get(key).offset());
            if(get(key).status_timestamp == value.record().status_timestamp){
                hashTable.put(key, new activeHashedValue(compactionPath, value.offset()));
            }
        }
        ///////////
        // This should be done carefully
        ///////////
        compactObj.collect_old_files();
        //////////
        //////////
        IncrementNumberOfCompacted();
    }

    private void IncrementNumberOfCompacted() {
        NumberOfCompactedFiles++;
    }
    private boolean checkCompact(){
        return NumberOf_Non_CompactedFiles >= FilesNumberLimit;
    }
    public void handleMessage(Record rec) {
        String currentFilePath = getCurrentFilePath(LastNonCompacted);
        this.put(rec, currentFilePath);
        //////////////////
        //////////////////
        // we can instead schedule a thread that runs every x seconds????????
        if(checkCompact()){
            this.compact(getDirectory(), NumberOfCompactedFiles, NumberOf_Non_CompactedFiles);
            for(long n : hashTable.keySet()){
                System.out.println(hashTable.get(n).filePath());
            }
        }
        //////////////////
        /////////////////
    }
}
