
def space    (){ '    ' }
def separator(){ ',\n' + space() }

/**
 * --------------------------------
 * JSON Array
 * --------------------------------
 */

def array ( Iterable    i ){ "[\n${ space() + ( i ?: [] ).collect{ myJson( it )}.join( separator()) }\n]" }
def array ( Iterator    i ){ array( i as Iterable ) }
def array ( Enumeration e ){ array( e as Iterable ) }
def array ( Object[]    o ){ array( o.toList()) }


/**
 * --------------------------------
 * JSON Hash
 * --------------------------------
 */

def hash( Map m ){
    final content = space() + ( m ?: [:] ).inject([]){ List list, Object key, Object value ->
        ( value != null ) ? list + "\"${ key }\": ${ myJson( value ) }" :
                            list
    }.join( separator())
    "{\n${ content }\n}"
}


/**
 * Renders Object provided as JSON Boolean, Number, String, Array or Hash
 */
@SuppressWarnings([ 'GroovyMultipleReturnPointsPerMethod' ])
def myJson ( Object o ) {

    if ( o == null ){ return String.valueOf( null ) }

    // noinspection GroovyAssignabilityCheck
    switch ( o ) {
        case Boolean:
        case Number:
            return o
        case Iterable:
            return array(( Iterable ) o )
        case Iterator:
            return array(( Iterator ) o )
        case Enumeration:
            return array(( Enumeration ) o )
        case Object[]:
            return array(( Object[] ) o )
        case Map:
            return hash(( Map ) o )
        default:
            return o.toString().with {
                it.contains( '\n' ) ? array( it.split( "\n" )) : "\"${ it }\""
            }
    }
}


/**
 * Renders Object provided as JSON
 */
@SuppressWarnings([ 'JavaStylePropertiesInvocation' ])
def json( Object o ) {

    [{ Object ob -> new groovy.json.JsonBuilder( ob ).toPrettyString()},
     { Object ob -> new com.google.gson.Gson().toJson( ob )},
     { Object ob -> myJson( ob )}].
    inject( null ){ Object result, Closure c ->
        try {
            /**
             * Return previously calculated result or attempt to calculate it.
             * Last closure does not fail so it'll be used if previous attempts do fail.
             */
            result ?: c( o )
        }
        catch ( Throwable t ) {
            // https://wiki.jenkins-ci.org/display/JENKINS/Logging
            final logger = java.util.logging.LogManager.logManager.getLogger( 'hudson.WebAppMain' )
            logger.warning( String.format( "Failed to JSON object of type %s: %s", o.getClass().name, t ))
            null
        }
    }
}
