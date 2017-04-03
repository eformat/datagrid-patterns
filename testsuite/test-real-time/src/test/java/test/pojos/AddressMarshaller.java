package test.pojos;

import java.io.IOException;

import org.infinispan.protostream.MessageMarshaller;

public class AddressMarshaller implements MessageMarshaller<Address> {

   @Override
   public String getTypeName() {
      return "test.User.Address";
   }

   @Override
   public Class<? extends Address> getJavaClass() {
      return Address.class;
   }

   @Override
   public Address readFrom(ProtoStreamReader reader) throws IOException {
      String street = reader.readString("street");
      String postCode = reader.readString("postCode");
      int number = reader.readInt("number");

      Address address = new Address();
      address.setStreet(street);
      address.setPostCode(postCode);
      address.setNumber(number);
      return address;
   }

   @Override
   public void writeTo(ProtoStreamWriter writer, Address address) throws IOException {
      writer.writeString("street", address.getStreet());
      writer.writeString("postCode", address.getPostCode());
      writer.writeInt("number", address.getNumber());
   }
}
