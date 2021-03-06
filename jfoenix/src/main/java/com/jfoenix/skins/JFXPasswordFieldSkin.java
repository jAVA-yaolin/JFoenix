/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.skins;

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.transitions.CachedTransition;
import com.jfoenix.validation.base.ValidatorBase;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
import javafx.animation.Animation.Status;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.lang.reflect.Field;

/**
 * <h1>Material Design PasswordField Skin</h1>
 *
 * @author Shadi Shaheen
 * @version 2.0
 * @since 2017-01-25
 */
public class JFXPasswordFieldSkin extends TextFieldSkin {

    private boolean invalid = true;

    private StackPane line = new StackPane();
    private StackPane focusedLine = new StackPane();
    private Label errorLabel = new Label();
    private StackPane errorIcon = new StackPane();
    private HBox errorContainer = new HBox();
    private StackPane promptContainer = new StackPane();

    private Text promptText;
    private Pane textPane;

    private ParallelTransition transition;
    private Timeline hideErrorAnimation;
    private CachedTransition promptTextUpTransition;
    private CachedTransition promptTextDownTransition;
    private CachedTransition promptTextColorTransition;

    private double initScale = 0.05;
    private Scale promptTextScale = new Scale(1, 1, 0, 0);
    private Scale scale = new Scale(initScale, 1);
    private Timeline linesAnimation = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(scale.xProperty(), initScale, Interpolator.EASE_BOTH),
            new KeyValue(focusedLine.opacityProperty(), 0, Interpolator.EASE_BOTH)),
        new KeyFrame(Duration.millis(1),
            new KeyValue(focusedLine.opacityProperty(), 1, Interpolator.EASE_BOTH)),
        new KeyFrame(Duration.millis(160),
            new KeyValue(scale.xProperty(), 1, Interpolator.EASE_BOTH))
    );

    private Paint oldPromptTextFill;
    private BooleanBinding usePromptText = Bindings.createBooleanBinding(this::usePromptText,
        getSkinnable().textProperty(),
        getSkinnable().promptTextProperty());

    public JFXPasswordFieldSkin(JFXPasswordField field) {
        super(field);
        // initial styles
        field.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
        field.setPadding(new Insets(4, 0, 4, 0));

        // add style classes
        errorLabel.getStyleClass().add("error-label");
        line.getStyleClass().add("input-line");
        focusedLine.getStyleClass().add("input-focused-line");

        // draw lines
        line.setPrefHeight(1);
        line.setTranslateY(1); // translate = prefHeight + init_translation
        line.setBackground(new Background(new BackgroundFill(((JFXPasswordField) getSkinnable()).getUnFocusColor(),
            CornerRadii.EMPTY, Insets.EMPTY)));
        if (getSkinnable().isDisabled()) {
            line.setBorder(new Border(new BorderStroke(((JFXPasswordField) getSkinnable()).getUnFocusColor(),
                BorderStrokeStyle.DASHED,
                CornerRadii.EMPTY,
                new BorderWidths(1))));
            line.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT,
                CornerRadii.EMPTY, Insets.EMPTY)));
        }

        // focused line
        focusedLine.setPrefHeight(2);
        focusedLine.setTranslateY(0); // translate = prefHeight + init_translation(-1)
        focusedLine.setBackground(new Background(new BackgroundFill(((JFXPasswordField) getSkinnable()).getFocusColor(),
            CornerRadii.EMPTY, Insets.EMPTY)));
        focusedLine.setOpacity(0);
        focusedLine.getTransforms().add(scale);

        // error container
        errorContainer.getChildren().setAll(new StackPane(errorLabel), errorIcon);
        errorContainer.setAlignment(Pos.CENTER_LEFT);
        errorContainer.setPadding(new Insets(4,0,0,0));
        errorContainer.setSpacing(8);
        errorContainer.setVisible(false);
        errorContainer.setOpacity(0);
        StackPane.setAlignment(errorLabel, Pos.CENTER_LEFT);
        HBox.setHgrow(errorLabel.getParent(), Priority.ALWAYS);


        getChildren().addAll(line, focusedLine, promptContainer, errorContainer);


        // add listeners
        errorContainer.visibleProperty().addListener((o, oldVal, newVal) -> {
            // show the error label if it's not shown
            if (newVal) {
                new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(errorContainer.opacityProperty(),
                        1,
                        Interpolator.EASE_BOTH))).play();
            }
        });

        field.labelFloatProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                JFXUtilities.runInFX(this::createFloatingLabel);
            } else {
                promptText.visibleProperty().bind(usePromptText);
            }
            createFocusTransition();
        });

        field.activeValidatorProperty().addListener((o, oldVal, newVal) -> {
            if (textPane != null) {
                if (!((JFXPasswordField) getSkinnable()).isDisableAnimation()) {
                    if (hideErrorAnimation != null && hideErrorAnimation.getStatus() == Status.RUNNING) {
                        hideErrorAnimation.stop();
                    }
                    if (newVal != null) {
                        hideErrorAnimation = new Timeline(new KeyFrame(Duration.millis(160),
                            new KeyValue(errorContainer.opacityProperty(),
                                0,
                                Interpolator.EASE_BOTH)));
                        hideErrorAnimation.setOnFinished(finish -> {
                            errorContainer.setVisible(false);
                            JFXUtilities.runInFX(() -> showError(newVal));
                        });
                        hideErrorAnimation.play();
                    } else {
                        JFXUtilities.runInFX(this::hideError);
                    }
                } else {
                    if (newVal != null) {
                        JFXUtilities.runInFXAndWait(() -> showError(newVal));
                    } else {
                        JFXUtilities.runInFXAndWait(this::hideError);
                    }
                }
            }
        });

        field.focusColorProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null) {
                focusedLine.setBackground(new Background(new BackgroundFill(newVal, CornerRadii.EMPTY, Insets.EMPTY)));
                if (((JFXPasswordField) getSkinnable()).isLabelFloat()) {
                    promptTextColorTransition = new CachedTransition(textPane, new Timeline(
                        new KeyFrame(Duration.millis(1300),
                            new KeyValue(promptTextFill, newVal, Interpolator.EASE_BOTH)))) {
                        {
                            setDelay(Duration.millis(0));
                            setCycleDuration(Duration.millis(160));
                        }

                        protected void starting() {
                            super.starting();
                            oldPromptTextFill = promptTextFill.get();
                        }
                    };
                    // reset transition
                    transition = null;
                }
            }
        });
        field.unFocusColorProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null) {
                line.setBackground(new Background(new BackgroundFill(newVal, CornerRadii.EMPTY, Insets.EMPTY)));
            }
        });

        // handle animation on focus gained/lost event
        field.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                focus();
            } else {
                unFocus();
            }
        });

        // handle text changing at runtime
        field.textProperty().addListener((o, oldVal, newVal) -> {
            if (!getSkinnable().isFocused() && ((JFXPasswordField) getSkinnable()).isLabelFloat()) {
                if (newVal == null || newVal.isEmpty()) {
                    animateFloatingLabel(false);
                } else {
                    animateFloatingLabel(true);
                }
            }
        });

        field.disabledProperty().addListener((o, oldVal, newVal) -> {
            line.setBorder(newVal ? new Border(new BorderStroke(((JFXPasswordField) getSkinnable()).getUnFocusColor(),
                BorderStrokeStyle.DASHED,
                CornerRadii.EMPTY,
                new BorderWidths(line.getHeight()))) : Border.EMPTY);
            line.setBackground(new Background(new BackgroundFill(newVal ? Color.TRANSPARENT : ((JFXPasswordField) getSkinnable())
                .getUnFocusColor(),
                CornerRadii.EMPTY, Insets.EMPTY)));
        });

        // prevent setting prompt text fill to transparent when text field is focused (override java transparent color if the control was focused)
        promptTextFill.addListener((o, oldVal, newVal) -> {
            if (Color.TRANSPARENT.equals(newVal) && ((JFXPasswordField) getSkinnable()).isLabelFloat()) {
                promptTextFill.set(oldVal);
            }
        });

    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        super.layoutChildren(x, y, w, h);

        // change control properties if and only if animations are stopped
        if (transition == null || transition.getStatus() == Status.STOPPED) {
            if (getSkinnable().isFocused() && ((JFXPasswordField) getSkinnable()).isLabelFloat()) {
                promptTextFill.set(((JFXPasswordField) getSkinnable()).getFocusColor());
            }
        }

        if (invalid) {
            invalid = false;
            textPane = (Pane) this.getChildren().get(0);
            // create floating label
            createFloatingLabel();
            // to position the prompt node properly
            super.layoutChildren(x, y, w, h);
            // update validation container
            if (((JFXPasswordField) getSkinnable()).getActiveValidator() != null) {
                updateValidationError();
            }
            // focus
            createFocusTransition();
            if (getSkinnable().isFocused()) {
                focus();
            }
        }

        focusedLine.resizeRelocate(x, getSkinnable().getHeight(), w, focusedLine.prefHeight(-1));
        line.resizeRelocate(x, getSkinnable().getHeight(), w, line.prefHeight(-1));

        final double errorContainerWidth = w - errorIcon.prefWidth(-1);
        errorContainer.resizeRelocate(x,getSkinnable().getHeight() + focusedLine.getHeight(),
            w,errorLabel.prefHeight(errorContainerWidth)
              + errorContainer.snappedBottomInset()
              + errorContainer.snappedTopInset());
        scale.setPivotX(w / 2);
    }

    private void updateValidationError() {
        if (hideErrorAnimation != null && hideErrorAnimation.getStatus() == Status.RUNNING) {
            hideErrorAnimation.stop();
        }
        hideErrorAnimation = new Timeline(
            new KeyFrame(Duration.millis(160),
                new KeyValue(errorContainer.opacityProperty(), 0, Interpolator.EASE_BOTH)));
        hideErrorAnimation.setOnFinished(finish -> {
            errorContainer.setVisible(false);
            showError(((JFXPasswordField) getSkinnable()).getActiveValidator());
        });
        hideErrorAnimation.play();
    }


    private void createFloatingLabel() {
        if (((JFXPasswordField) getSkinnable()).isLabelFloat()) {
            if (promptText == null) {
                // get the prompt text node or create it
                boolean triggerFloatLabel = false;
                if (textPane.getChildren().get(0) instanceof Text) {
                    promptText = (Text) textPane.getChildren().get(0);
                } else {
                    Field field;
                    try {
                        field = TextFieldSkin.class.getDeclaredField("promptNode");
                        field.setAccessible(true);
                        createPromptNode();
                        field.set(this, promptText);
                        // replace parent promptNode with promptText field
                        triggerFloatLabel = true;
                    } catch (NoSuchFieldException | SecurityException | IllegalAccessException | IllegalArgumentException ex) {
                        ex.printStackTrace();
                    }
                }
                promptText.getTransforms().add(promptTextScale);
                promptContainer.getChildren().add(promptText);

                if (triggerFloatLabel) {
                    promptText.setTranslateY(-textPane.getHeight());
                    promptTextScale.setX(0.85);
                    promptTextScale.setY(0.85);
                }
            }

            promptTextUpTransition = new CachedTransition(textPane, new Timeline(
                new KeyFrame(Duration.millis(1300),
                    new KeyValue(promptText.translateYProperty(),
                        -textPane.getHeight(),
                        Interpolator.EASE_BOTH),
                    new KeyValue(promptTextScale.xProperty(), 0.85, Interpolator.EASE_BOTH),
                    new KeyValue(promptTextScale.yProperty(), 0.85, Interpolator.EASE_BOTH)))) {{
                setDelay(Duration.millis(0));
                setCycleDuration(Duration.millis(240));
            }};

            promptTextColorTransition = new CachedTransition(textPane, new Timeline(
                new KeyFrame(Duration.millis(1300),
                    new KeyValue(promptTextFill,
                        ((JFXPasswordField) getSkinnable()).getFocusColor(),
                        Interpolator.EASE_BOTH)))) {
                {
                    setDelay(Duration.millis(0));
                    setCycleDuration(Duration.millis(160));
                }

                protected void starting() {
                    super.starting();
                    oldPromptTextFill = promptTextFill.get();
                }

            };

            promptTextDownTransition = new CachedTransition(textPane, new Timeline(
                new KeyFrame(Duration.millis(1300),
                    new KeyValue(promptText.translateYProperty(), 0, Interpolator.EASE_BOTH),
                    new KeyValue(promptTextScale.xProperty(), 1, Interpolator.EASE_BOTH),
                    new KeyValue(promptTextScale.yProperty(), 1, Interpolator.EASE_BOTH)))) {{
                setDelay(Duration.millis(0));
                setCycleDuration(Duration.millis(240));
            }};
            promptTextDownTransition.setOnFinished((finish) -> {
                promptText.setTranslateY(0);
                promptTextScale.setX(1);
                promptTextScale.setY(1);
            });
            promptText.visibleProperty().unbind();
            promptText.visibleProperty().set(true);
        }
    }

    private void createPromptNode() {
        promptText = new Text();
        promptText.setManaged(false);
        promptText.getStyleClass().add("text");
        promptText.visibleProperty().bind(usePromptText);
        promptText.fontProperty().bind(getSkinnable().fontProperty());
        promptText.textProperty().bind(getSkinnable().promptTextProperty());
        promptText.fillProperty().bind(promptTextFill);
        promptText.setLayoutX(1);
    }

    private void focus() {
        // in case the method request layout is not called before focused
        // this bug is reported while editing TreeTableView cells
        if (textPane == null) {
            Platform.runLater(this::focus);
        } else {
            // create the focus animations
            if (transition == null) {
                createFocusTransition();
            }
            transition.play();
        }
    }

    private void createFocusTransition() {
        transition = new ParallelTransition();
        if (((JFXPasswordField) getSkinnable()).isLabelFloat()) {
            transition.getChildren().add(promptTextUpTransition);
            transition.getChildren().add(promptTextColorTransition);
        }
        transition.getChildren().add(linesAnimation);
    }

    private void unFocus() {
        if (transition != null) {
            transition.stop();
        }
        scale.setX(initScale);
        focusedLine.setOpacity(0);
        if (((JFXPasswordField) getSkinnable()).isLabelFloat() && oldPromptTextFill != null) {
            promptTextFill.set(oldPromptTextFill);
            if (usePromptText()) {
                promptTextDownTransition.play();
            }
        }
    }

    /**
     * this method is called when the text property is changed when the
     * field is not focused (changed in code)
     *
     * @param up
     */
    private void animateFloatingLabel(boolean up) {
        if (promptText == null) {
            Platform.runLater(() -> animateFloatingLabel(up));
        } else {
            if (transition != null) {
                transition.stop();
                transition.getChildren().remove(promptTextUpTransition);
                transition = null;
            }
            if (up && promptText.getTranslateY() == 0) {
                promptTextDownTransition.stop();
                promptTextUpTransition.play();
            } else if (!up) {
                promptTextUpTransition.stop();
                promptTextDownTransition.play();
            }
        }
    }

    private boolean usePromptText() {
        String txt = getSkinnable().getText();
        String promptTxt = getSkinnable().getPromptText();
        return (txt == null || txt.isEmpty()) && promptTxt != null
               && !promptTxt.isEmpty() && !promptTextFill.get().equals(Color.TRANSPARENT);
    }

    private void showError(ValidatorBase validator) {
        // set text in error label
        errorLabel.setText(validator.getMessage());
        // show error icon
        Node icon = validator.getIcon();
        errorIcon.getChildren().clear();
        if (icon != null) {
            errorIcon.getChildren().setAll(icon);
            StackPane.setAlignment(icon, Pos.CENTER_RIGHT);
        }
        errorContainer.setVisible(true);
    }

    private void hideError() {
        // clear error label text
        errorLabel.setText(null);
        // clear error icon
        errorIcon.getChildren().clear();
        // reset the height of the text field
        // hide error container
        errorContainer.setVisible(false);
    }
}
