package org.apache.maven.wagon.shared.http;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.wagon.TransferFailedException;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Html File List Parser.
 */
public class HtmlFileListParser
{
    // Apache Fancy Index Sort Headers
    private static final Pattern APACHE_INDEX_SKIP = Pattern.compile( "\\?[CDMNS]=.*" );

    // URLs with excessive paths.
    private static final Pattern URLS_WITH_PATHS = Pattern.compile( "/[^/]*/" );

    // URLs that to a parent directory.
    private static final Pattern URLS_TO_PARENT = Pattern.compile( "\\.\\./" );

    // mailto urls
    private static final Pattern MAILTO_URLS = Pattern.compile( "mailto:.*" );

    private static final Pattern[] SKIPS =
        new Pattern[]{ APACHE_INDEX_SKIP, URLS_WITH_PATHS, URLS_TO_PARENT, MAILTO_URLS };

    /**
     * Fetches a raw HTML from a provided InputStream, parses it, and returns the file list.
     *
     * @param stream the input stream.
     * @return the file list.
     * @throws TransferFailedException if there was a problem fetching the raw html.
     */
    public static List<String> parseFileList( String baseurl, InputStream stream ) throws TransferFailedException
    {
        try
        {
            final Set<String> list = new HashSet<>();
            final URI baseURI = new URI( baseurl );

            ParserDelegator parserDelegator = new ParserDelegator();
            HTMLEditorKit.ParserCallback parserCallback = new HTMLEditorKit.ParserCallback()
            {
                public void handleText( final char[] data, final int pos )
                {
                }

                public void handleStartTag( HTML.Tag tag, MutableAttributeSet attribute, int pos )
                {
                    if ( tag == HTML.Tag.A )
                    {
                        String address = (String) attribute.getAttribute( HTML.Attribute.HREF );
                         // The abs:href loses directories, so we deal with absolute paths ourselves below in cleanLink
                        if ( address != null )
                        {
                            String clean = cleanLink( baseURI, address );
                            if ( isAcceptableLink( clean ) )
                            {
                                list.add( clean );
                            }
                        }
                    }
                }

                public void handleEndTag( HTML.Tag t, final int pos )
                {
                }

                public void handleSimpleTag( HTML.Tag t, MutableAttributeSet a, final int pos )
                {
                }

                public void handleComment( final char[] data, final int pos )
                {
                }

                public void handleError( final java.lang.String errMsg, final int pos )
                {
                }
            };
            parserDelegator.parse( new InputStreamReader( stream, StandardCharsets.UTF_8 ), parserCallback, false );

            return new ArrayList<>( list );
        }
        catch ( URISyntaxException e )
        {
            throw new TransferFailedException( "Unable to parse as base URI: " + baseurl, e );
        }
        catch ( IOException e )
        {
            throw new TransferFailedException( "I/O error reading HTML listing of artifacts: " + e.getMessage(), e );
        }
    }

    private static String cleanLink( URI baseURI, String link )
    {
        if ( link == null || link.length() == 0 )
        {
            return "";
        }

        String ret = link;

        try
        {
            URI linkuri = new URI( ret );
            if ( link.startsWith( "/" ) )
            {
                linkuri = baseURI.resolve( linkuri );
            }
            URI relativeURI = baseURI.relativize( linkuri ).normalize();
            ret = relativeURI.toASCIIString();
            if ( ret.startsWith( baseURI.getPath() ) )
            {
                ret = ret.substring( baseURI.getPath().length() );
            }

            ret = URLDecoder.decode( ret, "UTF-8" );
        }
        catch ( URISyntaxException | UnsupportedEncodingException e )
        {
            // ignore
        }

        return ret;
    }

    private static boolean isAcceptableLink( String link )
    {
        if ( link == null || link.length() == 0 )
        {
            return false;
        }

        for ( Pattern pattern : SKIPS )
        {
            if ( pattern.matcher( link ).find() )
            {
                return false;
            }
        }

        return true;
    }
}