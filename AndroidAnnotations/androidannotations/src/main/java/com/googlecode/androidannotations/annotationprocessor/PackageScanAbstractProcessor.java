/**
 * Copyright (C) 2010-2011 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.googlecode.androidannotations.annotationprocessor;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;

/**
 * Extends {@link AbstractProcessor} to override
 * {@link AbstractProcessor#getSupportedAnnotationTypes()}, enabling usage of
 * {@link SupportedAnnotationPackage} on a {@link Processor}.
 * 
 * @author Pierre-Yves Ricau
 */
public abstract class PackageScanAbstractProcessor extends AbstractProcessor {

	/**
	 * If the processor class is annotated with
	 * {@link SupportedAnnotationPackage} , return an unmodifiable set with the
	 * set of strings corresponding to the array of classes in the package set.
	 * If the class is not so annotated, the
	 * {@link AbstractProcessor#getSupportedAnnotationTypes()} method is called.
	 * 
	 * @return the names of the annotation classes supported by this processor,
	 *         or {@link AbstractProcessor#getSupportedAnnotationTypes()} result
	 *         if none
	 */
	public Set<String> getSupportedAnnotationTypes() {

		SupportedAnnotationPackage sap = getClass().getAnnotation(SupportedAnnotationPackage.class);
		if (sap == null) {
			if (isInitialized())
				processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "No " + SupportedAnnotationPackage.class.getSimpleName() + " annotation " + "found on " + this.getClass().getName() + ", returning parent method result.");
			return super.getSupportedAnnotationTypes();
		} else
			return findAnnotationsInPackage(sap.value());
	}

	private Set<String> findAnnotationsInPackage2(String packageName) {
		
		packageName = packageName.replace('.', '/');

		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		List<FileWithPackage> filesInPackage = new ArrayList<FileWithPackage>();

		try {
			Enumeration<URL> urls = classLoader.getResources(packageName);

			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				File dir = new File(url.toURI());
				for (File f : dir.listFiles()) {
					filesInPackage.add(new FileWithPackage(f, packageName));
				}
			}
		} catch (Exception e) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Error while scanning AndroidAnnotations package: "+e.getMessage());
			return Collections.emptySet();
		}

		Stack<FileWithPackage> stack = new Stack<FileWithPackage>();
		stack.addAll(filesInPackage);

		Set<String> annotationNames = new HashSet<String>();
		while (!stack.empty()) {
			FileWithPackage fileWithPackage = stack.pop();
			File file = fileWithPackage.file;
			if (file.isFile()) {
				String fileName = file.getName();
				if (fileName.endsWith(".class")) {
					String annotationName = fileName.substring(0, fileName.length() - ".class".length());
					String annotationQualifiedName = fileWithPackage.filePackage + "." + annotationName;
					annotationNames.add(annotationQualifiedName);
				}
			} else {
				/*
				 * We scan subpackages
				 */
				String newFilePackage = fileWithPackage.filePackage + "." + file.getName();
				for (File newFile : file.listFiles()) {
					stack.add(new FileWithPackage(newFile, newFilePackage));
				}
			}
		}
		return annotationNames;
	}

	private static class FileWithPackage {
		public final File file;
		public final String filePackage;

		public FileWithPackage(File file, String filePackage) {
			this.file = file;
			this.filePackage = filePackage;
		}
	}
	
	private Set<String> findAnnotationsInPackage(String packageName) {
		try {
			List<Class> classes = getClasses(packageName);
			HashSet<String> result = new HashSet<String>();
			for(Class clazz : classes) {
				result.add(clazz.getName());
			}
			return result;
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Collections.emptySet();
	}
	
    /**
     * Scans all classes accessible from the context class loader which belong to the given package and subpackages.
     *
     * @param packageName The base package
     * @return The classes
     * @throws ClassNotFoundException
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
	private static List<Class> getClasses(String packageName)
            throws ClassNotFoundException, IOException 
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        assert classLoader != null;
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<File>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String fileName = resource.getFile();
            String fileNameDecoded = URLDecoder.decode(fileName, "UTF-8");
            dirs.add(new File(fileNameDecoded));
        }
        ArrayList<Class> classes = new ArrayList<Class>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    /**
     * Recursive method used to find all classes in a given directory and subdirs.
     *
     * @param directory   The base directory
     * @param packageName The package name for classes found inside the base directory
     * @return The classes
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("unchecked")
	private static List<Class> findClasses(File directory, String packageName) throws ClassNotFoundException 
	{
        List<Class> classes = new ArrayList<Class>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        for (File file : files) {
        	String fileName = file.getName();
            if (file.isDirectory()) {
                assert !fileName.contains(".");
            	classes.addAll(findClasses(file, packageName + "." + fileName));
            } else if (fileName.endsWith(".class") && !fileName.contains("$")) {
            	Class _class;
				try {
					_class = Class.forName(packageName + '.' + fileName.substring(0, fileName.length() - 6));
				} catch (ExceptionInInitializerError e) {
					// happen, for example, in classes, which depend on 
					// Spring to inject some beans, and which fail, 
					// if dependency is not fulfilled
					_class = Class.forName(packageName + '.' + fileName.substring(0, fileName.length() - 6),
							false, Thread.currentThread().getContextClassLoader());
				}
				classes.add(_class);
            }
        }
        return classes;
    }

}
