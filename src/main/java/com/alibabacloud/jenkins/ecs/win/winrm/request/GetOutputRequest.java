package com.alibabacloud.jenkins.ecs.win.winrm.request;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.alibabacloud.jenkins.ecs.win.winrm.soap.Namespaces;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;

public class GetOutputRequest extends AbstractWinRMRequest {

    private final String shellId, commandId;

    public GetOutputRequest(URL url, String shellId, String commandId) {
        super(url);
        this.shellId = shellId;
        this.commandId = commandId;
    }

    @Override
    protected void construct() {
        try {
            defaultHeader().action(new URI("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/Receive")).resourceURI(new URI("http://schemas.microsoft.com/wbem/wsman/1/windows/shell/cmd")).shellId(shellId);

            Element body = DocumentHelper.createElement(QName.get("Receive", Namespaces.NS_WIN_SHELL));
            body.addElement(QName.get("DesiredStream", Namespaces.NS_WIN_SHELL)).addAttribute("CommandId", commandId).addText("stdout stderr");
            setBody(body);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Error while building request content", e);
        }
    }

}
