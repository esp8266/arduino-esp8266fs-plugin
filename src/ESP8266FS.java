/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Tool to put the contents of the sketch's "data" subfolder
  into an SPIFFS partition image and upload it to an ESP8266 MCU

  Copyright (c) 2015 Hristo Gochkov (ficeto at ficeto dot com)

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package com.esp8266.mkspiffs;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;

import processing.app.PreferencesData;
import processing.app.Editor;
import processing.app.Base;
import processing.app.BaseNoGui;
import processing.app.Platform;
import processing.app.Sketch;
import processing.app.tools.Tool;
import processing.app.helpers.ProcessUtils;
import processing.app.debug.TargetPlatform;

import org.apache.commons.codec.digest.DigestUtils;
import processing.app.helpers.FileUtils;

import cc.arduino.files.DeleteFilesOnShutdown;

/**
 * Example Tools menu entry.
 */
public class ESP8266FS implements Tool {
  Editor editor;


  public void init(Editor editor) {
    this.editor = editor;
  }


  public String getMenuTitle() {
    return "ESP8266 Sketch Data Upload";
  }

  private int listenOnProcess(String[] arguments){
      try {
        final Process p = ProcessUtils.exec(arguments);
        Thread thread = new Thread() {
          public void run() {
            try {
              InputStreamReader reader = new InputStreamReader(p.getInputStream());
              int c;
              while ((c = reader.read()) != -1)
                  System.out.print((char) c);
              reader.close();
              
              reader = new InputStreamReader(p.getErrorStream());
              while ((c = reader.read()) != -1)
                  System.err.print((char) c);
              reader.close();
            } catch (Exception e){}
          }
        };
        thread.start();
        int res = p.waitFor();
        thread.join();
        return res;
      } catch (Exception e){
        return -1;
      }
    }

  private void sysExec(final String[] arguments){
    Thread thread = new Thread() {
      public void run() {
        try {
          if(listenOnProcess(arguments) != 0){
            editor.statusError("SPIFFS Upload failed!");
          } else {
            editor.statusNotice("SPIFFS Image Uploaded");
          }
        } catch (Exception e){
          editor.statusError("SPIFFS Upload failed!");
        }
      }
    };
    thread.start();
  }

  private String getBuildFolderPath(Sketch s) {
    // first of all try the getBuildPath() function introduced with IDE 1.6.12
    // see commit arduino/Arduino#fd1541eb47d589f9b9ea7e558018a8cf49bb6d03
    try {
      String buildpath = s.getBuildPath().getAbsolutePath();
      return buildpath;
    }
    catch (IOException er) {
       editor.statusError(er);
    }
    catch (Exception er) {
      try {
        File buildFolder = FileUtils.createTempFolder("build", DigestUtils.md5Hex(s.getMainFilePath()) + ".tmp");
        return buildFolder.getAbsolutePath();
      }
      catch (IOException e) {
        editor.statusError(e);
      }
      catch (Exception e) {
        // Arduino 1.6.5 doesn't have FileUtils.createTempFolder
        // String buildPath = BaseNoGui.getBuildFolder().getAbsolutePath();
        java.lang.reflect.Method method;
        try {
          method = BaseNoGui.class.getMethod("getBuildFolder");
          File f = (File) method.invoke(null);
          return f.getAbsolutePath();
        } catch (SecurityException ex) {
          editor.statusError(ex);
        } catch (IllegalAccessException ex) {
          editor.statusError(ex);
        } catch (InvocationTargetException ex) {
          editor.statusError(ex);
        } catch (NoSuchMethodException ex) {
          editor.statusError(ex);
        }
      }
    }
    return "";
  }

  private long getIntPref(String name){
    String data = BaseNoGui.getBoardPreferences().get(name);
    if(data == null || data.contentEquals("")) return 0;
    if(data.startsWith("0x")) return Long.parseLong(data.substring(2), 16);
    else return Integer.parseInt(data);
  }

  private void createAndUpload(){
    if(!PreferencesData.get("target_platform").contentEquals("esp8266")){
      System.err.println();
      editor.statusError("SPIFFS Not Supported on "+PreferencesData.get("target_platform"));
      return;
    }

    if(!BaseNoGui.getBoardPreferences().containsKey("build.spiffs_start") || !BaseNoGui.getBoardPreferences().containsKey("build.spiffs_end")){
      System.err.println();
      editor.statusError("SPIFFS Not Defined for "+BaseNoGui.getBoardPreferences().get("name"));
      return;
    }
    long spiStart, spiEnd, spiPage, spiBlock;
    try {
      spiStart = getIntPref("build.spiffs_start");
      spiEnd = getIntPref("build.spiffs_end");
      spiPage = getIntPref("build.spiffs_pagesize");
      if(spiPage == 0) spiPage = 256;
      spiBlock = getIntPref("build.spiffs_blocksize");
      if(spiBlock == 0) spiBlock = 4096;
    } catch(Exception e){
      editor.statusError(e);
      return;
    }

    TargetPlatform platform = BaseNoGui.getTargetPlatform();
    
    //Make sure mkspiffs binary exists
    String mkspiffsCmd;
    if(PreferencesData.get("runtime.os").contentEquals("windows"))
        mkspiffsCmd = "mkspiffs.exe";
    else
        mkspiffsCmd = "mkspiffs";

    File tool = new File(platform.getFolder() + "/tools", mkspiffsCmd);
    if (!tool.exists() || !tool.isFile()) {
      tool = new File(platform.getFolder() + "/tools/mkspiffs", mkspiffsCmd);
      if (!tool.exists()) {
        tool = new File(PreferencesData.get("runtime.tools.mkspiffs.path"), mkspiffsCmd);
        if (!tool.exists()) {
            System.err.println();
            editor.statusError("SPIFFS Error: mkspiffs not found!");
            return;
        }
      }
    }
    
    Boolean isNetwork = false;
    File espota = new File(platform.getFolder()+"/tools");
    File esptool = new File(platform.getFolder()+"/tools");
    String serialPort = PreferencesData.get("serial.port");
    String pythonCmd = PreferencesData.get("runtime.os").contentEquals("windows") ? "python3.exe" : "python3";
    String uploadCmd = "";
    
    //make sure the serial port or IP is defined
    if (serialPort == null || serialPort.isEmpty()) {
      System.err.println();
      editor.statusError("SPIFFS Error: serial port not defined!");
      return;
    }

    // Find upload.py, don't fail if not present for backwards compat
    File uploadPyFile = new File(platform.getFolder()+"/tools", "upload.py");
    if (uploadPyFile.exists() && uploadPyFile.isFile()) {
      uploadCmd = uploadPyFile.getAbsolutePath();
    }
    // Find python.exe if present, don't fail if not found for backwards compat
    String[] paths = { platform.getFolder()+"/tools", platform.getFolder()+"/tools/python3", PreferencesData.get("runtime.tools.python3.path") };
    for (String s: paths) {
      File toolPyFile = new File(s, pythonCmd);
      if (toolPyFile.exists() && toolPyFile.isFile() && toolPyFile.canExecute()) {
        pythonCmd = toolPyFile.getAbsolutePath();
        break;
      }
    }
    // pythonCmd now points to either an installed exe with full path or just plain "python3(.exe)"

    //find espota if IP else find esptool
    if(serialPort.split("\\.").length == 4){
      isNetwork = true;
      String espotaCmd = "espota.py";
      espota = new File(platform.getFolder()+"/tools", espotaCmd);
      if(!espota.exists() || !espota.isFile()){
        System.err.println();
        editor.statusError("SPIFFS Error: espota not found!");
        return;
      }
    } else {
      String esptoolCmd = platform.getTool("esptool").get("cmd");
      esptool = new File(platform.getFolder()+"/tools", esptoolCmd);
      if(!esptool.exists() || !esptool.isFile()){
        esptool = new File(platform.getFolder()+"/tools/esptool", esptoolCmd);
        if(!esptool.exists()){
          esptool = new File(PreferencesData.get("runtime.tools.esptool.path"), esptoolCmd);
          if (!esptool.exists() && uploadCmd.isEmpty()) {
              System.err.println();
              editor.statusError("SPIFFS Error: esptool not found!");
              return;
          }
        }
      }
    }
    
    //load a list of all files
    int fileCount = 0;
    File dataFolder = new File(editor.getSketch().getFolder(), "data");
    if (!dataFolder.exists()) {
        dataFolder.mkdirs();
    }
    if(dataFolder.exists() && dataFolder.isDirectory()){
      File[] files = dataFolder.listFiles();
      if(files.length > 0){
        for(File file : files){
          if((file.isDirectory() || file.isFile()) && !file.getName().startsWith(".")) fileCount++;
        }
      }
    }

    String dataPath = dataFolder.getAbsolutePath();
    String toolPath = tool.getAbsolutePath();
    String sketchName = editor.getSketch().getName();
    String imagePath = getBuildFolderPath(editor.getSketch()) + "/" + sketchName + ".spiffs.bin";
    String resetMethod = BaseNoGui.getBoardPreferences().get("upload.resetmethod");
    String uploadSpeed = BaseNoGui.getBoardPreferences().get("upload.speed");
    String uploadAddress = BaseNoGui.getBoardPreferences().get("build.spiffs_start");

    

    Object[] options = { "Yes", "No" };
    String title = "SPIFFS Create";
    String message = "No files have been found in your data folder!\nAre you sure you want to create an empty SPIFFS image?";

    if(fileCount == 0 && JOptionPane.showOptionDialog(editor, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != JOptionPane.YES_OPTION){
      System.err.println();
      editor.statusError("SPIFFS Warning: mkspiffs canceled!");
      return;
    }

    editor.statusNotice("SPIFFS Creating Image...");
    System.out.println("[SPIFFS] data    : "+dataPath);
    System.out.println("[SPIFFS] size    : "+((spiEnd - spiStart)/1024));
    System.out.println("[SPIFFS] page    : "+spiPage);
    System.out.println("[SPIFFS] block   : "+spiBlock);

    try {
      if(listenOnProcess(new String[]{toolPath, "-c", dataPath, "-p", spiPage+"", "-b", spiBlock+"", "-s", (spiEnd - spiStart)+"", imagePath}) != 0){
        System.err.println();
        editor.statusError("SPIFFS Create Failed!");
        return;
      }
    } catch (Exception e){
      editor.statusError(e);
      editor.statusError("SPIFFS Create Failed!");
      return;
    }

    editor.statusNotice("SPIFFS Uploading Image...");
    System.out.println("[SPIFFS] upload  : "+imagePath);
    
    if(isNetwork){
      System.out.println("[SPIFFS] IP       : "+serialPort);
      System.out.println();
      sysExec(new String[]{pythonCmd, espota.getAbsolutePath(), "-i", serialPort, "-s", "-f", imagePath});
    } else {
      System.out.println("[SPIFFS] address  : "+uploadAddress);
      System.out.println("[SPIFFS] reset    : "+resetMethod);
      System.out.println("[SPIFFS] port     : "+serialPort);
      System.out.println("[SPIFFS] speed    : "+uploadSpeed);
      if (!uploadCmd.isEmpty()) {
        System.out.println("[SPIFFS] python   : "+pythonCmd);
        System.out.println("[SPIFFS] uploader : "+uploadCmd);
      }
      System.out.println();
      if (!uploadCmd.isEmpty()) {
        sysExec(new String[]{pythonCmd, uploadCmd, "--chip", "esp8266", "--port", serialPort, "--baud", uploadSpeed, "write_flash", uploadAddress, imagePath});
      } else {
        sysExec(new String[]{esptool.getAbsolutePath(), "-cd", resetMethod, "-cb", uploadSpeed, "-cp", serialPort, "-ca", uploadAddress, "-cf", imagePath});
      }
    }
  }

  public void run() {
    createAndUpload();
  }
}
