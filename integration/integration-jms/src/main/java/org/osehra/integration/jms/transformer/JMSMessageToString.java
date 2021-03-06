package org.osehra.integration.jms.transformer;

import org.osehra.integration.core.transformer.Transformer;
import org.osehra.integration.core.transformer.TransformerException;
import org.osehra.integration.util.NullChecker;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.TextMessage;

import org.springframework.beans.factory.annotation.Required;
import org.w3c.dom.Document;

/**
 * Convert JMS Message to String.
 * 
 * @author Julian Jewel
 */
public class JMSMessageToString implements Transformer<Message, String> {

	/**
	 * Default buffer size.
	 */
	private static final int BYTE_BUFFER_SIZE = 2048;

	/**
	 * Get the message string from the bytes message.
	 * 
	 * @param bytesMessage
	 *            the bytes message.
	 * @return the string
	 * @throws JMSException
	 *             exception when converting bytes message to string
	 */
	private String getMessageString(final BytesMessage bytesMessage)
			throws JMSException {
		bytesMessage.reset();
		int len = 0;
		final byte[] buffer = new byte[JMSMessageToString.BYTE_BUFFER_SIZE];
		final StringBuffer strBuffer = new StringBuffer();
		while ((len = bytesMessage.readBytes(buffer)) > 0) {
			strBuffer.append(new String(buffer, 0, len));
		}
		bytesMessage.reset();
		return strBuffer.toString();
	}

	/**
	 * Set the XML to String transformer.
	 * 
	 * @param theXmlToString
	 *            the xml to string transformer
	 */
	@Required
	public void setXmlToString(
			final Transformer<Document, String> theXmlToString) {
	}

	/**
	 * Transform the JMS message to String.
	 * 
	 * @param jmsMessage
	 *            the input JMS message
	 * @return the converted string
	 * @throws TransformerException
	 *             an exception when transforming the JMS message to string
	 */
	@Override
	public String transform(final Message jmsMessage)
			throws TransformerException {
		if (NullChecker.isEmpty(jmsMessage)) {
			return null;
		}

		try {
			if (jmsMessage instanceof BytesMessage) {
				final String messageString = this
						.getMessageString((BytesMessage) jmsMessage);
				return messageString;
				// } else if (jmsMessage instanceof XMLMessage) {
				// final Document doc = ((XMLMessage) jmsMessage).getDocument();
				// return this.xmlToString.transform(doc);
			} else if (jmsMessage instanceof TextMessage) {
				final String str = ((TextMessage) jmsMessage).getText();
				return str;
			} else if (ObjectMessage.class.isInstance(jmsMessage)) {
				final Object object = ((ObjectMessage) jmsMessage).getObject();
				if (String.class.isInstance(object)) {
					return (String) object;
				} else {
					throw new RuntimeException(
							"Only String objects are allowed!");
				}
			} else {
				throw new RuntimeException("Unknown message type!");
			}
		} catch (final JMSException ex) {
			throw new TransformerException(ex);
		}
	}

}
