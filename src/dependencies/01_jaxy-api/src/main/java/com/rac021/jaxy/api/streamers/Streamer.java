
package com.rac021.jaxy.api.streamers ;

import java.util.List ;
import java.util.ArrayList ;
import javax.inject.Inject ;
import java.io.IOException ;
import java.util.logging.Logger ;
import javax.xml.bind.Marshaller ;
import javax.xml.bind.JAXBContext ;
import java.util.stream.IntStream ;
import javax.annotation.PreDestroy ;
import java.util.stream.Collectors ;
import javax.xml.bind.JAXBException ;
import java.util.concurrent.Callable ;
import javax.annotation.PostConstruct ;
import javax.persistence.EntityManager ;
import javax.ws.rs.core.MultivaluedMap ;
import com.rac021.jaxy.api.manager.IDto ;
import java.util.concurrent.BlockingQueue ;
import javax.persistence.PersistenceContext ;
import com.rac021.jaxy.api.security.ISignOn ;
import com.rac021.jaxy.api.manager.IResource ;
import javax.enterprise.context.RequestScoped ;
import java.util.concurrent.ExecutionException ;
import java.util.concurrent.ArrayBlockingQueue ;
import com.rac021.jaxy.api.analyzer.SqlAnalyzer ;
import com.rac021.jaxy.api.root.ServicesManager ;
import static com.rac021.jaxy.api.logger.LoggerFactory.getLogger ;
import static com.rac021.jaxy.api.streamers.DefaultStreamerConfigurator.* ;
;

/**
 *
 * @author yahiaoui
 */

@RequestScoped
public abstract class Streamer implements IStreamer {
 
    public static final String PU = "MyPU" ;
    
    @PersistenceContext  ( unitName  = Streamer.PU )
    private EntityManager entityManager            ;

    @Inject 
    protected ServicesManager servicesManager      ;
    
    protected  BlockingQueue<IDto> dtos                                 ;
    
    protected  int                 maxThreads                           ;
    
    protected  ResourceWraper      resource                             ;
    
    private    List<String>        keepFieldsList       = null          ;
    
    protected  boolean             isFinishedProcess    = false         ;
    
    protected  BlockingQueue<Exception> exceptions      = new ArrayBlockingQueue<>(5) ;
    
    @PreDestroy   
    public void cleanup() {
    }
     
    @PostConstruct
    public void init() {
    }
    
    public Streamer() { }

    public void producerScheduler()                 {
        
        resource.initResource( selectSize * ratio ) ;

        List<Callable<Void>> jobs = IntStream.range( 0 , maxThreads )
                                             .mapToObj( i -> (Callable<Void>) new Producer() )
                                             .collect(Collectors.toList());
        try {
                poolProducer.invokeAll(jobs)
                            .stream()
                            .map ( future -> { try { return future.get() ; }
                                   catch ( InterruptedException | ExecutionException e) {
                                     exceptions.add(e) ;
                                     throw new RuntimeException(e)      ;
                                   } } )
                            .count() ;

        } catch( RuntimeException | InterruptedException e ) {
        } finally {
          isFinishedProcess = true ;
        }
    }

    protected void configureStreamer() {
  
      this.maxThreads = servicesManager.getOrDefaultMaxThreadsFor ( ISignOn.SERVICE_NAME.get() ) ;
      
      dtos = new ArrayBlockingQueue<>( ratio * selectSize * this.maxThreads ) ;
       
    }

    protected class Producer implements Callable    {
        
        @Override
        public Void call() throws Exception {
           
                while ( ! resource.isFinished() )   {

                    long count = resource.getDtoIterable( entityManager, 
                                                          selectSize * ratio ,
                                                          keepFieldsList     )
                                         .stream()
                                         .map( (localDto) -> {
                                               try {
                                                   dtos.put(localDto)   ;
                                                   return localDto      ;
                                               } catch (InterruptedException ex)   {
                                                   resource.setIsFinished(true)    ;
                                                   throw new RuntimeException(ex)  ;
                                               }})
                                         .count() ;

                   if (count == 0 ) {

                       resource.setIsFinished(true) ;
                       break                        ;

                   } 
                }

                return null ;
        }
    }
    
    public void rootResourceWraper( IResource resource     ,
                                    Class     dto          , 
                                    String    keepFields   , 
                                    MultivaluedMap <String ,
                                    String> ... filedsFilters ) {
        
        this.keepFieldsList = toListNames(keepFields) ;
        
        String  queryWithAppliedFilters  = null       ;
        
        if( filedsFilters != null )                   {
            try {
            queryWithAppliedFilters = SqlAnalyzer.generateQueryAccordingFieldsFilters (
                                                     servicesManager.getQueriesByResourceName ( 
                                                      resource.getClass().getName() 
                                                     ) ,  filedsFilters[0] )                  ;
            } catch( Exception ex )            {
                throw new RuntimeException(ex) ;
            }
        }

        this.resource = new ResourceWraper( resource, dto, queryWithAppliedFilters ) ; 
    }
    
    protected List<String> toListNames( String names )     {
        
      if( names != null && ! names.isEmpty() ) {
         List<String> l = new ArrayList<>()    ;
         String[] split = names.split("-")     ;
         for( String fieldName : split)  {
             l.add( fieldName.trim().replaceAll(" +", "")) ;
         }
         return l ;
      }
      
      return null ;
    }
    
   
    protected Marshaller getMashellerWithJSONProperties() {
        
        Marshaller localMarshaller= null ;
        
        try {
            JAXBContext jc  = JAXBContext.newInstance(resource.getDto(), EmptyPojo.class)      ;
            localMarshaller = jc.createMarshaller()                                            ;
            localMarshaller.setProperty("eclipselink.media-type", "application/json")          ;
            localMarshaller.setProperty("eclipselink.json.include-root" , Boolean.FALSE)       ;
            localMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE)        ;
        } catch (JAXBException ex) {
            exceptions.add(ex)     ;
        }
        
        return localMarshaller     ;
    }
    
    protected Marshaller getMashellerWithXMLProperties() {
       
        Marshaller localMarshaller = null ;
        try {
            JAXBContext jxbContect = JAXBContext.newInstance( resource.getDto(), EmptyPojo.class) ;
            localMarshaller        = jxbContect.createMarshaller()                                ;
            localMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE)           ;
            localMarshaller.setProperty("com.sun.xml.bind.xmlHeaders", "")                        ;
            localMarshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE)                   ;
            
        } catch (JAXBException ex) {
            exceptions.add(ex)     ;
        }
        return localMarshaller     ;
    }
   
    
    @Override
    public void setStreamerConfigurator( IStreamerConfigurator iStreamerConfigurator ) {
        this.maxThreads = iStreamerConfigurator.getMaxThreads() ;
    }
    
    
    protected boolean checkIfExceptionsAndNotify( String messagefrom      ,
                                                  boolean rootException ) throws IOException     {

        /** Check and flush exception before close Writer . */
        if( ! exceptions.isEmpty() ) {
           
            while( ! exceptions.isEmpty() )               {
                  Exception exception = exceptions.poll() ;
                  if( rootException ) {
                      while ( exception.getCause() != null) {
                         exception = (Exception) exception.getCause() ;
                      }
                  }
                  /** Will invock the RuntimeExceptionMapper that will LOG and 
                      send response to the client . */
                  throw new RuntimeException(exception) ;
            }

            return true ;
        }
        
        return false ;
    }
}
