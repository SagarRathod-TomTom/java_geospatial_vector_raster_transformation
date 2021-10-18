\**

<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<java.version>11</java.version>
		<jts.version>1.18.0</jts.version>
		<geotools.version>25.2</geotools.version>
</properties>

<!-- Vector to Raster -->
<dependency>
<groupId>org.geotools</groupId>
	<artifactId>gt-process-feature</artifactId>
	<version>${geotools.version}</version>
</dependency>

<dependency>
	<groupId>org.geotools</groupId>
	<artifactId>gt-geotiff</artifactId>
	<version>${geotools.version}</version>
</dependency>

<dependency>
	<groupId>org.geotools</groupId>
	<artifactId>gt-coverage</artifactId>
	<version>${geotools.version}</version>
</dependency>

<dependency>
	<groupId>org.geotools</groupId>
	<artifactId>gt-epsg-hsql</artifactId>
	<version>${geotools.version}</version>
</dependency>

**/

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.gce.geotiff.GeoTiffFormat;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.process.vector.VectorToRasterProcess;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.coverage.grid.GridCoverageWriter;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VectorToRaster {

    public static void main(String[] args) throws ParseException, IOException, FactoryException, TransformException {


        Hints.putSystemDefault(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true);

        CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:4326");
        CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:3857");
        MathTransform transform4326To3857 = CRS.findMathTransform(sourceCRS, targetCRS);

        String wkt = "LineString (-149.92810844189477848 61.12748261743160327, -149.92761488377453816 61.12705338418207646, -149.92761488377453816 61.12705338418207646)";

        WKTReader wktReader = new WKTReader();

        final LineString lineString = (LineString) wktReader.read(wkt);

        Geometry targetGeometry = JTS.transform(lineString, transform4326To3857);

        targetGeometry = targetGeometry.buffer(2);

        MathTransform transform3857To4326 = CRS.findMathTransform(targetCRS, sourceCRS);
        targetGeometry = JTS.transform(targetGeometry, transform3857To4326);


        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Location");
        builder.setCRS(DefaultGeographicCRS.WGS84);
        //builder.setDefaultGeometry("LineString");

        builder.add("the_geom", Polygon.class);
        builder.length(15).add("Name", String.class); // <- 15 chars width for name field
        builder.add("number", Integer.class);
        SimpleFeatureType featureType = builder.buildFeatureType();

        SimpleFeatureBuilder simpleFeatureBuilder = new SimpleFeatureBuilder(featureType);
        simpleFeatureBuilder.add(targetGeometry);

        SimpleFeature simpleFeature = simpleFeatureBuilder.buildFeature(null);

        List<SimpleFeature> lineFeatures = new ArrayList<>();
        lineFeatures.add(simpleFeature);

        SimpleFeatureCollection simpleFeatureCollection = new ListFeatureCollection(featureType, lineFeatures);

        VectorToRasterProcess vectorToRasterProcess = new VectorToRasterProcess();

        Geometry geometryEnvelope =  targetGeometry.getEnvelope();
        System.out.println(geometryEnvelope);


        ReferencedEnvelope referencedEnvelope = new ReferencedEnvelope(lineString.getEnvelopeInternal(),
                DefaultGeographicCRS.WGS84);

        GridCoverage2D gridCoverage2D = vectorToRasterProcess.execute(simpleFeatureCollection, 224, 224, "Name",
                String.valueOf(255), referencedEnvelope, null);

        File file = new File("geo-tagged.tiff");

        final GeoTiffFormat format = new GeoTiffFormat();

        final GridCoverageWriter writer = format.getWriter(file);
        writer.write(gridCoverage2D, null);
        writer.dispose();

    }
}
