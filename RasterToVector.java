import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.process.raster.PolygonExtractionProcess;
import org.opengis.feature.simple.SimpleFeatureType;
import org.geotools.data.shapefile.ShapefileDataStore;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RasterToVector {

    /**
     * Maven Dependencies
     * <dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-process-raster</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     * 		<dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-epsg-extension</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     * 		<dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-epsg-hsql</artifactId>
     * 			<version>11.1</version>
     * 		</dependency>
     * 	    <dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-shapefile</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     * 		<dependency>
     * 			<groupId>org.geotools</groupId>
     * 			<artifactId>gt-geopkg</artifactId>
     * 			<version>${geotools.version}</version>
     * 		</dependency>
     *
     * @param args
     * @throws IOException
     */

    public static void main(String args[]) throws IOException {

        if(args.length < 2){
            System.out.println("Usage: java RasterToVector [input_raster_dir] [out_dir]");
            return;
        }

        String rasterDir = args[0];
        File rasterDirFile = new File(rasterDir);
        File rasterFiles[] = rasterDirFile.listFiles();

        String out_dir = args[1];
        String outfilepath = out_dir + "\\" + "vector.shp";
        File outfile = new File(outfilepath);

        SimpleFeatureType schema = null;
        ShapefileDataStore newDataStore = null;

        for(int i = 0; i < rasterFiles.length; i++){

            String filepath = rasterFiles[i].toString();
            if(!filepath.endsWith(".tiff"))
                continue;

            GeoTiffReader tiffReader = new GeoTiffReader(rasterFiles[i]);
            GridCoverage2D coverage = tiffReader.read(null);

            PolygonExtractionProcess process = new PolygonExtractionProcess();
            SimpleFeatureCollection simpleFeatureCollection =  process.execute(coverage, 0,
                    false, null, null,  null, null);

            if(schema == null) {
                schema = simpleFeatureCollection.getSchema();
                ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
                Map<String, Serializable> create = new HashMap<String, Serializable>();
                create.put("url", outfile.toURI().toURL());

                newDataStore = (ShapefileDataStore) factory.createNewDataStore(create);
                newDataStore.createSchema(schema);
            }

            Transaction transaction = new DefaultTransaction();
            SimpleFeatureStore featureStore = (SimpleFeatureStore) newDataStore.getFeatureSource();
            featureStore.setTransaction(transaction);
            try {
                featureStore.addFeatures(simpleFeatureCollection);
                transaction.commit();
            } catch (Exception problem) {
                problem.printStackTrace();
                transaction.rollback();
            } finally {
                transaction.close();
            }
            System.out.println("Completed " + i  + "/" + rasterFiles.length + " " + rasterFiles[i]);
        }


        // Geo package support in geotools
        //https://docs.geotools.org/latest/userguide/library/data/geopackage.html

        String geopkgoutfilepath = out_dir + "\\" + "vector.gpk";
        File gpkoutfile = new File(geopkgoutfilepath);
        GeoPackage geopkg = new GeoPackage(gpkoutfile);
        geopkg.init();
        FeatureEntry entry = new FeatureEntry();
        geopkg.add(entry, newDataStore.getFeatureSource(), null);
        geopkg.close();

    }

}
