package burp;

import com.securityevaluators.burpeasyrequestsaver.DataSource;
import com.securityevaluators.burpeasyrequestsaver.DataType;
import com.securityevaluators.burpeasyrequestsaver.Util;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.nio.file.StandardOpenOption;

public class BurpExtender implements IBurpExtender, IContextMenuFactory
{

    private PrintWriter stdout;
    private PrintWriter stderr;
    private IExtensionHelpers helpers;

    private String ExtensionName = "Easy Request Saver";
    private String Version = "1.1";

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
    {
        // obtain our output and error streams
        stdout = new PrintWriter(callbacks.getStdout(), true);
        stderr = new PrintWriter(callbacks.getStderr(), true);
        helpers = callbacks.getHelpers();

        // set our extension name
        callbacks.setExtensionName(ExtensionName);

        callbacks.registerContextMenuFactory(this);
        stdout.println(String.format("Loaded Easy Request Saver extension: %s %s", ExtensionName, Version));
    }

    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation iContextMenuInvocation) {
        int itemCount = iContextMenuInvocation.getSelectedMessages().length;
        if (itemCount < 1) return null;
        boolean plural = itemCount > 1;
        JMenuItem exportItem = new JMenu("Export InA ...");
        DataSource[] menuItemSources =
                {DataSource.REQUEST, DataSource.REQUEST, DataSource.RESPONSE, DataSource.RESPONSE};
        DataType[] menuItemTypes =
                {DataType.HEADER, DataType.BODY, DataType.HEADER, DataType.BODY};

        for(int i = 0; i < menuItemSources.length; i++)
        {
            DataSource source = menuItemSources[i];
            DataType type = menuItemTypes[i];
            JMenuItem item = new JMenuItem(source.getNoun() + " " + type.getNoun(plural));

            item.addActionListener((e) ->
                    saveItems(source, type, (Component) e.getSource(), iContextMenuInvocation.getSelectedMessages()));

            exportItem.add(item);
        }

        return Collections.singletonList(exportItem);
    }

    private void saveItems(DataSource source, DataType type, Component parentComponent, IHttpRequestResponse[] requests) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Specify a destination file name" + (requests.length > 1 ? " prefix" : ""));

        int result = chooser.showSaveDialog(parentComponent);
        if (result != JFileChooser.APPROVE_OPTION) return;
        Path chosenPath = chooser.getSelectedFile().toPath();
        if (requests.length == 1) {
            saveItem(chosenPath, requests[0], source, type);
            return;
        }
        //循环保存每个选中的消息
        for (int i = 0; i < requests.length; i++) {
            saveItem(chosenPath, requests[i], source, type);
        }
    }

    private void saveItem(Path path, IHttpRequestResponse request, DataSource source, DataType type) {

        int offset = (source == DataSource.REQUEST) ?
                helpers.analyzeRequest(request).getBodyOffset() :
                helpers.analyzeResponse(request.getResponse()).getBodyOffset();
        byte[] sourceData = (source == DataSource.REQUEST) ?
                request.getRequest() :
                request.getResponse();
        byte[] data = type == DataType.HEADER ?
                Arrays.copyOf(sourceData, offset) :
                Arrays.copyOfRange(sourceData, offset, sourceData.length);

        byte[] splitBytes = "\n====================================================\n".getBytes();
        try {
            // 创建一个新字节数组，用于存储分割线和数据
            byte[] combinedData = new byte[splitBytes.length + data.length];
            // 将分割线字节复制到新数组中
            System.arraycopy(splitBytes, 0, combinedData, 0, splitBytes.length);
            // 将数据字节复制到新数组中，从分割线字节后面的位置开始
            System.arraycopy(data, 0, combinedData, splitBytes.length, data.length);
            // 一次性写入文件，仅使用APPEND选项
            Files.write(path, combinedData, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            //CREATE：如果文件不存在，那么会创建一个新的文件；如果文件已经存在，此选项并不会影响文件内容。
            //APPEND：表示将数据写入文件时，数据会被追加到文件的末尾，而不是覆盖现有内容。
        } catch (IOException e) {
            e.printStackTrace(stderr);
        }
    }
}