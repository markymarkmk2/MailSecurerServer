/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.mailarchiv.Utilities;

import dimm.home.mailarchiv.LogicControl;
import dimm.home.mailarchiv.Main;
import dimm.home.mailarchiv.MandantContext;
import home.shared.mail.RFCFileMail;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.logging.Level;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 *
 * @author mw
 */
public class CryptTools
{
    MandantContext ctx;
    public enum ENC_MODE
    {
        ENCRYPT,
        DECRYPT
    };

    public CryptTools( MandantContext _ctx )
    {
        ctx = _ctx;
    }

    public  static String get_sha256( byte[] data )
    {

        MessageDigest md = null;
        try
        {
            md = MessageDigest.getInstance("SHA-256");
            return new String(md.digest(data));
        }
        catch (NoSuchAlgorithmException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }
/*
    private static String get_sha1( byte[] data )
    {

        MessageDigest md = null;
        try
        {
            md = MessageDigest.getInstance("SHA-1");
            return new String(md.digest(data));
        }
        catch (NoSuchAlgorithmException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }
 * */
    public static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * Computes RFC 2104-compliant HMAC signature.
     * * @param data
     * The data to be signed.
     * @param key
     * The signing key.
     * @return
     * The Base64-encoded RFC 2104-compliant HMAC signature.
     * @throws
     * java.security.SignatureException when signature generation fails
     */
    public static String calculateRFC2104HMAC( String data, String key )
            throws java.security.SignatureException
    {
        String result;
        try
        {

            // get an hmac_sha1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);

            // get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);

            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(new Hex().decode(data.getBytes()));

            // base64-encode the hmac
            result = new String(Base64.encodeBase64(rawHmac));

        }
        catch (Exception e)
        {
            throw new SignatureException("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }
/*
    private byte[] encrypt_aes( byte[] data ) throws NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException
    {
        try
        {
            // Get the KeyGenerator
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128); // 192 and 256 bits may not be available

            // Generate the secret key specs.
            SecretKey skey = kgen.generateKey();
            byte[] raw = skey.getEncoded();
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");

            // Instantiate the cipher
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            byte[] encrypted = cipher.doFinal(data);
            return encrypted;
        }
        catch (NoSuchAlgorithmException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;

    }
 * */
    /*    Cipher ecipher;
    Cipher dcipher;
     * */
    // Iteration count

    public static byte[] crypt( byte[] data, String passPhrase, ENC_MODE encrypt )
    {
        int iterationCount = Main.get_prefs().get_KeyPBEIteration();
        byte[] salt = Main.get_prefs().get_KeyPBESalt();
        String algorithm = Main.get_prefs().get_KeyAlgorithm();

        try
        {
            KeySpec keySpec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount);
            SecretKey key = SecretKeyFactory.getInstance(algorithm).generateSecret(keySpec);

            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);
            // Create the ciphers
            if (encrypt == ENC_MODE.ENCRYPT)
            {
                cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
            }
            else
            {
                cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
            }


            // Encrypt
            byte[] enc = cipher.doFinal(data);

            return enc;
        }

        catch (NoSuchPaddingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (NoSuchAlgorithmException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidKeySpecException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (IllegalBlockSizeException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (BadPaddingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidKeyException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidAlgorithmParameterException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static String crypt( String str, String passPhrase, ENC_MODE encrypt )
    {
        try
        {
            // Encode the string into bytes using utf-8
            byte[] utf8 = str.getBytes("UTF8");

            // Encrypt
            byte[] enc = crypt( utf8, passPhrase, encrypt );

            // Encode bytes to base64 to get a string
            return new String( Base64.encodeBase64(enc) );
            //return new sun.misc.BASE64Encoder().encode(enc);
        }
        catch (UnsupportedEncodingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    public static String crypt_internal( String str, ENC_MODE encrypt )
    {
        String passPhrase = Main.get_prefs().get_InternalPassPhrase();
        try
        {
            byte[] src;
            if (encrypt == ENC_MODE.DECRYPT)
            {
                src = Base64.decodeBase64(str.getBytes("UTF8"));
            }
            else
            {
                // Encode the string into bytes using utf-8
                src = str.getBytes("UTF8");
            }

            // Encrypt
            byte[] trg = crypt( src, passPhrase, encrypt );
            if (trg == null)
                return null;

            if (encrypt == ENC_MODE.ENCRYPT)
            {
                // Encode bytes to base64 to get a string
                return new String( Base64.encodeBase64(trg) );
            }

            return new String( trg, "UTF8" );
            //return new sun.misc.BASE64Encoder().encode(enc);
        }
        catch (UnsupportedEncodingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private OutputStream create_crypt_AES_outstream( MandantContext context, OutputStream os, String passPhrase, ENC_MODE encrypt )
    {
        int iterationCount = Main.get_prefs().get_KeyPBEIteration();
        byte[] salt = Main.get_prefs().get_KeyPBESalt();
//        String algorithm = context.getPrefs().get_KeyAlgorithm();

        try
        {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("AES");
            byte[] keyData = passPhrase.getBytes(); // 16 bytes for AES
            SecretKeySpec keySpec = new SecretKeySpec(keyData, "AES");
            Key key = keyFactory.generateSecret(keySpec);

/*            KeySpec keySpec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount);
            SecretKey key = SecretKeyFactory.getInstance(algorithm).generateSecret(keySpec);
*/
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);

            cipher.init(encrypt == ENC_MODE.ENCRYPT ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, paramSpec);

            os = new CipherOutputStream(os, cipher);

            return os;
        }




        catch (InvalidKeyException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidAlgorithmParameterException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (NoSuchPaddingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidKeySpecException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (NoSuchAlgorithmException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private InputStream create_crypt_AES_instream( MandantContext context, InputStream is, String passPhrase, ENC_MODE encrypt )
    {
        int iterationCount = Main.get_prefs().get_KeyPBEIteration();
        byte[] salt = Main.get_prefs().get_KeyPBESalt();
//        String algorithm = context.getPrefs().get_KeyAlgorithm();

        try
        {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("AES");
            byte[] keyData = passPhrase.getBytes(); // 16 bytes for AES
            SecretKeySpec keySpec = new SecretKeySpec(keyData, "AES");
            Key key = keyFactory.generateSecret(keySpec);

/*            KeySpec keySpec = new PBEKeySpec(passPhrase.toCharArray(), salt, iterationCount);
            SecretKey key = SecretKeyFactory.getInstance( algorithm ).generateSecret(keySpec);
*/
            Cipher cipher = Cipher.getInstance(key.getAlgorithm());
            AlgorithmParameterSpec paramSpec = new PBEParameterSpec(salt, iterationCount);

            cipher.init(encrypt == ENC_MODE.ENCRYPT ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key, paramSpec);

            is = new CipherInputStream(is, cipher);

            return is;
        }




        catch (InvalidKeyException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidAlgorithmParameterException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (NoSuchPaddingException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (InvalidKeySpecException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }        catch (NoSuchAlgorithmException ex)
        {
            LogManager.log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public static void main( String[] args )
    {
        LogicControl instance;


        try
        {
            instance = new LogicControl();
            instance.initialize();

            System.out.println("testcrypt");

            String name = "j:\\tmp\\enc.tst";
            String TEST = "1234567890";

            // PREFS FOR ARGS, ARGS HABEN PRIO
            Main.create_prefs();
            Security.addProvider(new BouncyCastleProvider() );

            MandantContext ctx = instance.get_mandant_by_id(1);

            RFCFileMail rfc = new RFCFileMail(new File(name), true);
            rfc.set_encryption( ctx.getPrefs().get_password(), Main.get_prefs().get_KeyPBEIteration(), Main.get_prefs().get_KeyPBESalt() );
            OutputStream os = rfc.open_outputstream();

            os.write(TEST.getBytes());
            os.flush();
            os.close();

            InputStream is = rfc.open_inputstream();

            byte[] in_data = new byte[TEST.getBytes().length];

            is.read(in_data);
            is.close();

            String intest = new String(in_data);


            ctx.getTempFileHandler().clean_up();
            if (TEST.compareTo(intest) != 0)
            {
                System.out.println("Encrypt / decrypt failed: Wrote " + TEST + " got " + intest);
            }
            else
            {
                rfc.delete();

            }


        System.out.println("create_dupl_temp_file");



        File f = ctx.getTempFileHandler().create_temp_file("UnitTest", "JU", "dat");

        FileOutputStream fos = new FileOutputStream(f);
        fos.write(new String("12345").getBytes());
        fos.close();
        FileInputStream fis = new FileInputStream(f);


        RFCFileMail result = instance.dump_msg_stream_to_temp_file(ctx.getMandant(), fis, "test", "test", "eml", false, true);
        if (f.length() !=  result.get_length())
            System.err.println("Length doensz fit");

        fis.close();

        ctx.getTempFileHandler().clean_up();
        if (f.exists())
                System.err.println("Source file is still there " + f.getAbsolutePath() );
        if (result.getFile().exists())
            System.err.println( "target file is still there " + result.getFile().getAbsolutePath() );

        }
        catch (Exception iOException)
        {
            iOException.printStackTrace();
        }
    }

}    

