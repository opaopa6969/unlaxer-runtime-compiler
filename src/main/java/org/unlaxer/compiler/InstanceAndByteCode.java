package org.unlaxer.compiler;

public class InstanceAndByteCode implements InstanceAndClassNameAndByteCode{
  final Object object;
  final byte[] bytes;
  final String className;
  public InstanceAndByteCode(Object object, byte[] bytes) {
    super();
    this.object = object;
    this.bytes = bytes;
    className = object.getClass().getTypeName();
  }
  public String className() {
    return object.getClass().getTypeName();
  }
  @Override
  public byte[] byteCode() {
    return bytes;
  }
  @Override
  public Object instance() {
    return object;
  }
}