/**
 * This file is part of veraPDF Validation, a module of the veraPDF project.
 * Copyright (c) 2015, veraPDF Consortium <info@verapdf.org>
 * All rights reserved.
 *
 * veraPDF Validation is free software: you can redistribute it and/or modify
 * it under the terms of either:
 *
 * The GNU General public license GPLv3+.
 * You should have received a copy of the GNU General Public License
 * along with veraPDF Validation as the LICENSE.GPL file in the root of the source
 * tree.  If not, see http://www.gnu.org/licenses/ or
 * https://www.gnu.org/licenses/gpl-3.0.en.html.
 *
 * The Mozilla Public License MPLv2+.
 * You should have received a copy of the Mozilla Public License along with
 * veraPDF Validation as the LICENSE.MPL file in the root of the source tree.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.verapdf.gf.model.impl.pd.images;

import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSKey;
import org.verapdf.gf.model.factory.operators.GraphicState;
import org.verapdf.gf.model.impl.containers.StaticContainers;
import org.verapdf.gf.model.impl.pd.GFPDContentStream;
import org.verapdf.gf.model.impl.pd.GFPDGroup;
import org.verapdf.gf.model.impl.pd.GFPDSemanticContentStream;
import org.verapdf.gf.model.impl.pd.util.PDResourcesHandler;
import org.verapdf.model.baselayer.Object;
import org.verapdf.model.pdlayer.PDContentStream;
import org.verapdf.model.pdlayer.PDGroup;
import org.verapdf.model.pdlayer.PDXForm;
import org.verapdf.model.pdlayer.XFormTransparencyGroup;
import org.verapdf.model.pdlayer.ParentXFormTransparencyGroup;
import org.verapdf.pd.colors.PDColorSpace;
import org.verapdf.pd.structure.StructureElementAccessObject;
import org.verapdf.pdfa.flavours.PDFAFlavour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Maksim Bezrukov
 */
public class GFPDXForm extends GFPDXObject implements PDXForm {

	public static final String X_FORM_TYPE = "PDXForm";

	public static final String GROUP = "Group";
	public static final String CONTENT_STREAM = "contentStream";

	public static final String XFORM_TRANSPARENCY_GROUP = "xFormTransparencyGroup";
	public static final String PARENT_XFORM_TRANSPARENCY_GROUP = "parentXFormTransparencyGroup";

	private List<PDContentStream> contentStreams = null;
	private List<PDGroup> groups = null;
	private boolean groupContainsTransparency = false;
	private boolean contentStreamContainsTransparency = false;
	private final GraphicState inheritedGraphicState;
	private final String parentStructureTag;
	private final String parentsTags;
	private final PDColorSpace blendingColorSpace;

	public GFPDXForm(org.verapdf.pd.images.PDXForm simplePDObject, PDResourcesHandler resourcesHandler,
					 GraphicState inheritedGraphicState,
					 String parentStructureTag, String parentsTags) {
		super(simplePDObject, resourcesHandler.getExtendedResources(simplePDObject.getResources()), X_FORM_TYPE);
		this.inheritedGraphicState = inheritedGraphicState;
		this.parentStructureTag = parentStructureTag;
		this.parentsTags = parentsTags;
		this.blendingColorSpace = getBlendingColorSpace();
	}

	@Override
	public String getSubtype2() {
		ASAtom subtype2 = ((org.verapdf.pd.images.PDXForm) this.simplePDObject).getSubtype2();
		return subtype2 == null ? null : subtype2.getValue();
	}

	@Override
	public List<? extends Object> getLinkedObjects(String link) {
		switch (link) {
			case GROUP:
				return this.getGroup();
			case CONTENT_STREAM:
				return this.getContentStream();
			case XFORM_TRANSPARENCY_GROUP:
				return this.getXFormTransparencyGroup();
			case PARENT_XFORM_TRANSPARENCY_GROUP:
				return this.getParentXFormTransparencyGroup();
			default:
				return super.getLinkedObjects(link);
		}
	}

	private List<PDGroup> getGroup() {
		if (this.groups == null) {
			initializeGroups();
		}
		return this.groups;
	}

	public PDColorSpace getBlendingColorSpace() {
		org.verapdf.pd.PDGroup group = ((org.verapdf.pd.images.PDXForm) this.simplePDObject).getGroup();
		if (group == null || !ASAtom.TRANSPARENCY.equals(group.getSubtype())) {
			return null;
		}
		return group.getColorSpace();
	}

	@Override
	public Boolean getcontainsPS() {
		// See 7.11.5 in PDF 1.2 specification
		return this.simplePDObject.knownKey(ASAtom.PS);
	}

	@Override
	public Boolean getcontainsRef() {
		return this.simplePDObject.knownKey(ASAtom.REF);
	}

	@Override
	public Boolean getisUniqueSemanticParent() {
		if (!this.simplePDObject.knownKey(ASAtom.STRUCT_PARENTS)) {
			return true;
		}
		COSKey key = this.simplePDObject.getObject().getKey();
		if (key == null) {
			return true;
		}
		if (StaticContainers.getXFormKeysSet().contains(key)) {
			return false;
		}
		StaticContainers.getXFormKeysSet().add(key);
		return true;
	}

	@Override
	public String getID() {
		return null;
	}

	private List<PDContentStream> getContentStream() {
		if (this.contentStreams == null) {
			parseContentStream();
		}
		return this.contentStreams;
	}

	private void initializeGroups() {
		org.verapdf.pd.images.PDXForm form = (org.verapdf.pd.images.PDXForm) this.simplePDObject;
		org.verapdf.pd.PDGroup group = form.getGroup();
		if (group != null) {
			this.groupContainsTransparency = ASAtom.TRANSPARENCY.equals(group.getSubtype());
			List<PDGroup> groups = new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
			groups.add(new GFPDGroup(group, resourcesHandler.getObjectResources()));
			this.groups = Collections.unmodifiableList(groups);
		} else {
			this.groups = Collections.emptyList();
		}
	}

	private void parseContentStream() {
		List<PDContentStream> streams = new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
		GFPDContentStream gfContentStream;
		if (!PDFAFlavour.PDFUA_1.getPart().getFamily().equals(StaticContainers.getFlavour().getPart().getFamily())) {
			gfContentStream = new GFPDContentStream(
					(org.verapdf.pd.images.PDXForm) this.simplePDObject, resourcesHandler,
					this.inheritedGraphicState, new StructureElementAccessObject(this.simpleCOSObject),
					parentStructureTag, parentsTags);
		} else {
			gfContentStream = new GFPDSemanticContentStream(
					(org.verapdf.pd.images.PDXForm) this.simplePDObject, resourcesHandler,
					this.inheritedGraphicState, new StructureElementAccessObject(this.simpleCOSObject),
					parentStructureTag, parentsTags);
		}
		this.contentStreamContainsTransparency = gfContentStream.isContainsTransparency();
		streams.add(gfContentStream);
		this.contentStreams = streams;
	}

	/**
	 * @return true if current form object contains transparency group or transparency in its content stream
	 */
	public boolean containsTransparency() {
		if (groups == null) {
			initializeGroups();
		}
		if (contentStreams == null) {
			parseContentStream();
		}

		return groupContainsTransparency || contentStreamContainsTransparency;
	}

	private List<XFormTransparencyGroup> getXFormTransparencyGroup() {
		if (blendingColorSpace != null) {
			List<XFormTransparencyGroup> xFormTransparencyGroup = new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
			xFormTransparencyGroup.add(new GFXFormTransparencyGroup(blendingColorSpace));
			return Collections.unmodifiableList(xFormTransparencyGroup);
		}
		return Collections.emptyList();
	}

	private List<ParentXFormTransparencyGroup> getParentXFormTransparencyGroup() {
		if (blendingColorSpace != null) {
			List<ParentXFormTransparencyGroup> parentXFormTransparencyGroup = new ArrayList<>(MAX_NUMBER_OF_ELEMENTS);
			parentXFormTransparencyGroup.add(new GFParentXFormTransparencyGroup(StaticContainers.getXFormTransparencyColorSpace()));
			return Collections.unmodifiableList(parentXFormTransparencyGroup);
		}
		return Collections.emptyList();
	}
}
