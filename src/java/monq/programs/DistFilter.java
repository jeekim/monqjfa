/*+********************************************************************* 
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation
Foundation, Inc., 59 Temple Place - Suite 330, Boston MA 02111-1307, USA.
************************************************************************/

package monq.programs;

import java.io.*;
import monq.net.*;
import monq.clifj.*;
import monq.jfa.*;
import monq.stuff.*;

import java.util.*;

/**
 * <p>is a command line front-end wrapper for {@link
 * monq.net.DistPipeFilter}.</p>
 *
 * @author &copy; 2004, 2005 Harald Kirsch
 * @version $Revision: 1.10 $, $Date: 2006-02-09 12:38:23 $
 */
public class DistFilter {

  /**********************************************************************/
  /**
   * add key/value pairs found as consecutive submatches in ts
   * beginning at start to req.
   */
  private static void addKeys(PipelineRequest req, 
			      TextStore ts, int start,
			      StringBuilder fiddle) {
    for(int kk=start; kk<ts.getNumParts(); kk+=2) {
      String value = ts.getPart(kk+1);
      fiddle.setLength(0);
      int l = value.length();
      for(int jj=0; jj<l; jj++) {
	char ch = value.charAt(jj);
	if( ch=='\\' && jj+1<l && value.charAt(jj+1)==';' ) {
	  ch = ';';
	  jj += 1;
	}
	fiddle.append(value.charAt(jj));
      }
      req.put(ts.getPart(kk), fiddle.toString());
    }
  }
  /**********************************************************************/
  public static void main(String[] argv) 
    throws Exception
  {
    String prog = System.getProperty("argv0", "DistFilter");
      // String prog = System.getProperty("argv0", "FilterMain");
    Commandline cmd = new Commandline
      (prog,
       "use a distributed pipe to filter stdin to stdout",
       "address",
       "address information listing servers to contact. An address "
       +"looks like `host=xyz;port=12345' or like `svr=name'"
       ,1, Integer.MAX_VALUE);
    cmd.addOption
      (new LongOption
       ("-p", "port", 
	"local port to use, by default an arbitray one is chosen",
	1, 1, 1024, 65535, new Object[0]));

    cmd.addOption(new BooleanOption("-v",
				    "log server start/stop to stderr"));
    String[] dfltConfig = {'.'+File.separator+"servers"};
    cmd.addOption
      (new Option
       ("-c", "confdir",
	"config file directory containing server descriptions",
	1, 1, dfltConfig)
       );

    try {
      cmd.parse(argv);
    } catch( CommandlineException e ) {
      System.err.println(e.getMessage());
      System.exit(1);
    }

    int port = 0;
    if( cmd.available("-p") ) port = ((Long)cmd.getValue("-p")).intValue();

    // Read configuration data
    Map<String,FilterSvrInfo> serverConfig = 
        FilterSvrInfo.readAll(cmd.getStringValue("-c"));
    // We have to invert the order of the servers listed on the
    // command line for the convenience of the user
    String[] restArgs = cmd.getStringValues("--");
    List<PipelineRequest> reqs = new ArrayList<PipelineRequest>();

    String tailre = "(;(!"+PipelineRequest.KEYRE+")=(!([^;]|\\\\;)+))*";
    Regexp hostportre = new Regexp("host=(![^;]+);port=(![0-9]+)"+tailre);
    Regexp svrre = new Regexp("svr=(![^;]+)"+tailre);
    StringBuilder fiddle =  new StringBuilder();

    for(int i=0; i<restArgs.length; i++) {
      TextStore ts = null;
      PipelineRequest currentReq = null;
      if( hostportre.matches(restArgs[i]) ) {
	ts = hostportre.submatches();
	try {
	  currentReq = new PipelineRequest(ts.getPart(1),
					   Integer.parseInt(ts.getPart(2)));
	} catch( NumberFormatException e ) {
	  throw new Error("cannot parse `"+restArgs[i]+"', see cause", e);
	}
	addKeys(currentReq, ts, 3, fiddle);
	reqs.add(currentReq);
	continue;
      }

      if( svrre.matches(restArgs[i]) ) {
	ts = svrre.submatches();
	FilterSvrInfo svr = serverConfig.get(ts.getPart(1));
	if( svr==null ) {
	  System.err.println("server `"+ts.getPart(1)+"' unknown");
	  System.exit(1);
	}
	currentReq = svr.getRequest();
	reqs.add(currentReq);
	addKeys(currentReq, ts, 2, fiddle);
	continue;
      } 

      System.err.println(prog+": cannot parse request `"+restArgs[i]+"'");
      System.exit(1);

    }

    PipelineRequest[] address = reqs.toArray(new PipelineRequest[reqs.size()]);

    DistPipeFilter df;
    if( cmd.available("-v") ) df = new DistPipeFilter(port, 10, System.err);
    else df = new DistPipeFilter(port, 10);
    df.start();

    try {
      Pipe pin = new Pipe(4096);
      pin.setIn(System.in, false);
      InputStream in = df.open(address, pin);

      byte[] buf = new byte[4096];
      int l;
      while( -1!=(l=in.read(buf)) ) {
	System.out.write(buf, 0, l); 
	if( System.out.checkError() ) break;
      }
      df.close(in);
    } catch( ServiceCreateException e ) {
      e.printStackTrace();
    } catch( java.io.IOException e ) {
      if( e.getMessage().startsWith("Broken pipe") ) System.exit(1);
      throw e;
    }
    //System.out.println("shutting down now");
    df.shutdown();
  }
}
