/*
 * Copyright (c) 2003, 2024 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package jakarta.xml.bind.util;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLFilterImpl;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.XMLFilter;

/**
 * JAXP {@link javax.xml.transform.Source} implementation
 * that marshals a Jakarta XML Binding-generated object.
 *
 * <p>
 * This utility class is useful to combine Jakarta XML Binding with
 * other Java/XML technologies.
 *
 * <p>
 * The following example shows how to use Jakarta XML Binding to marshal a document
 * for transformation by XSLT.
 *
 * {@snippet :
 *  MyObject o = // get JAXB content tree
 *
 *  // jaxbContext is a JAXBContext object from which 'o' is created.
 *  JAXBSource source = new JAXBSource( jaxbContext, o );
 *
 *  // set up XSLT transformation
 *  TransformerFactory tf = TransformerFactory.newInstance();
 *  Transformer t = tf.newTransformer(new StreamSource("test.xsl"));
 *
 *  // run transformation
 *  t.transform(source,new StreamResult(System.out));
 * }
 *
 * <p>
 * The fact that JAXBSource derives from SAXSource is an implementation
 * detail. Thus, in general applications are strongly discouraged from
 * accessing methods defined on SAXSource. In particular,
 * the setXMLReader and setInputSource methods shall never be called.
 * The XMLReader object obtained by the getXMLReader method shall
 * be used only for parsing the InputSource object returned by
 * the getInputSource method.
 *
 * <p>
 * Similarly, the InputSource object obtained by the getInputSource
 * method shall be used only for being parsed by the XMLReader object
 * returned by the getXMLReader.
 *
 * @author
 * 	Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 * @since 1.6
 */
public class JAXBSource extends SAXSource {

    /**
     * Creates a new {@link javax.xml.transform.Source} for the given content object.
     *
     * @param   context
     *      JAXBContext that was used to create
     *      <code>contentObject</code>. This context is used
     *      to create a new instance of marshaller and must not be null.
     * @param   contentObject
     *      An instance of a Jakarta XML Binding-generated class, which will be
     *      used as a {@link javax.xml.transform.Source} (by marshalling it into XML).  It must
     *      not be null.
     * @throws JAXBException if an error is encountered while creating the
     * JAXBSource or if either of the parameters are null.
     */
    public JAXBSource( JAXBContext context, Object contentObject )
        throws JAXBException {

        this(
            ( context == null ) ?
                assertionFailed( Messages.format( Messages.SOURCE_NULL_CONTEXT ) ) :
                context.createMarshaller(),

            ( contentObject == null ) ?
                assertionFailed( Messages.format( Messages.SOURCE_NULL_CONTENT ) ) :
                contentObject);
    }

    /**
     * Creates a new {@link javax.xml.transform.Source} for the given content object.
     *
     * @param   marshaller
     *      A marshaller instance that will be used to marshal
     *      <code>contentObject</code> into XML. This must be
     *      created from a JAXBContext that was used to build
     *      <code>contentObject</code> and must not be null.
     * @param   contentObject
     *      An instance of a Jakarta XML Binding-generated class, which will be
     *      used as a {@link javax.xml.transform.Source} (by marshalling it into XML).  It must
     *      not be null.
     * @throws JAXBException if an error is encountered while creating the
     * JAXBSource or if either of the parameters are null.
     */
    public JAXBSource( Marshaller marshaller, Object contentObject )
        throws JAXBException {

        if( marshaller == null )
            throw new JAXBException(
                Messages.format( Messages.SOURCE_NULL_MARSHALLER ) );

        if( contentObject == null )
            throw new JAXBException(
                Messages.format( Messages.SOURCE_NULL_CONTENT ) );

        this.marshaller = marshaller;
        this.contentObject = contentObject;

        super.setXMLReader(pseudoParser);
        // pass a dummy InputSource. We don't care
        super.setInputSource(new InputSource());
    }

    private final Marshaller marshaller;
    private final Object contentObject;

    // this object will pretend as an XMLReader.
    // no matter what parameter is specified to the parse method,
    // it just parses the contentObject.
    private final XMLReader pseudoParser = new XMLReader() {
        @Override
        public boolean getFeature(String name) throws SAXNotRecognizedException {
            if(name.equals("http://xml.org/sax/features/namespaces"))
                return true;
            if(name.equals("http://xml.org/sax/features/namespace-prefixes"))
                return false;
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public void setFeature(String name, boolean value) throws SAXNotRecognizedException {
            if(name.equals("http://xml.org/sax/features/namespaces") && value)
                return;
            if(name.equals("http://xml.org/sax/features/namespace-prefixes") && !value)
                return;
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException {
            if( "http://xml.org/sax/properties/lexical-handler".equals(name) ) {
                return lexicalHandler;
            }
            throw new SAXNotRecognizedException(name);
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException {
            if( "http://xml.org/sax/properties/lexical-handler".equals(name) ) {
                this.lexicalHandler = (LexicalHandler)value;
                return;
            }
            throw new SAXNotRecognizedException(name);
        }

        private LexicalHandler lexicalHandler;

        // we will store this value but never use it by ourselves.
        private EntityResolver entityResolver;
        @Override
        public void setEntityResolver(EntityResolver resolver) {
            this.entityResolver = resolver;
        }
        @Override
        public EntityResolver getEntityResolver() {
            return entityResolver;
        }

        private DTDHandler dtdHandler;
        @Override
        public void setDTDHandler(DTDHandler handler) {
            this.dtdHandler = handler;
        }
        @Override
        public DTDHandler getDTDHandler() {
            return dtdHandler;
        }

        // SAX allows ContentHandler to be changed during the parsing,
        // but JAXB doesn't. So this repeater will sit between those
        // two components.
        private XMLFilter repeater = new XMLFilterImpl();

        @Override
        public void setContentHandler(ContentHandler handler) {
            repeater.setContentHandler(handler);
        }
        @Override
        public ContentHandler getContentHandler() {
            return repeater.getContentHandler();
        }

        private ErrorHandler errorHandler;
        @Override
        public void setErrorHandler(ErrorHandler handler) {
            this.errorHandler = handler;
        }
        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public void parse(InputSource input) throws SAXException {
            parse();
        }

        @Override
        public void parse(String systemId) throws SAXException {
            parse();
        }

        public void parse() throws SAXException {
            // parses a content object by using the given marshaller
            // SAX events will be sent to the repeater, and the repeater
            // will further forward it to an appropriate component.
            try {
                marshaller.marshal( contentObject, (XMLFilterImpl)repeater );
            } catch( JAXBException e ) {
                // wrap it to a SAXException
                SAXParseException se =
                    new SAXParseException( e.getMessage(),
                        null, null, -1, -1, e );

                // if the consumer sets an error handler, it is our responsibility
                // to notify it.
                if(errorHandler!=null)
                    errorHandler.fatalError(se);

                // this is a fatal error. Even if the error handler
                // returns, we will abort anyway.
                throw se;
            }
        }
    };

    /**
     * Hook to throw exception from the middle of a constructor chained call
     * to this
     */
    private static Marshaller assertionFailed( String message )
        throws JAXBException {

        throw new JAXBException( message );
    }
}
